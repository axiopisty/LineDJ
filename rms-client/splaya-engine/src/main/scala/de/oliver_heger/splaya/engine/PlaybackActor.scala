package de.oliver_heger.splaya.engine;

import java.io.Closeable
import java.util.Arrays

import scala.actors.Actor
import scala.collection.mutable.Queue

import org.slf4j.LoggerFactory

import de.oliver_heger.splaya.engine.io.SourceStreamWrapper
import de.oliver_heger.splaya.engine.io.SourceStreamWrapperFactory
import de.oliver_heger.splaya.engine.io.TempFile
import de.oliver_heger.splaya.engine.msg.ActorExited
import de.oliver_heger.splaya.engine.msg.ChunkPlayed
import de.oliver_heger.splaya.engine.msg.FlushPlayer
import de.oliver_heger.splaya.engine.msg.Gateway
import de.oliver_heger.splaya.engine.msg.PlayChunk
import de.oliver_heger.splaya.engine.msg.SkipCurrentSource
import de.oliver_heger.splaya.engine.msg.SourceReadError
import de.oliver_heger.splaya.engine.msg.StartPlayback
import de.oliver_heger.splaya.engine.msg.StopPlayback
import de.oliver_heger.splaya.AudioSource
import de.oliver_heger.splaya.PlaybackContext
import de.oliver_heger.splaya.PlaybackError
import de.oliver_heger.splaya.PlaybackPositionChanged
import de.oliver_heger.splaya.PlaybackSourceEnd
import de.oliver_heger.splaya.PlaybackSourceStart
import de.oliver_heger.splaya.PlaybackStarts
import de.oliver_heger.splaya.PlaybackStops
import de.oliver_heger.splaya.PlaylistEnd
import javax.sound.sampled.SourceDataLine

/**
 * An actor for handling audio playback.
 *
 * This actor receives messages when new audio data has been streamed to the
 * temporary buffer. Its task is to process the single audio streams and send
 * them chunk-wise to the ''line write actor''. It also has to manage a data
 * line for actually playing audio data.
 *
 * This actor and the [[de.oliver_heger.splaya.engine.LineWriteActor]] actor
 * play a kind of ping pong when processing audio streams: ''PlaybackActor''
 * sends a chunk of data to be played, ''LineWriteActor'' feeds it into the
 * line and sends a message back when this is complete. This causes
 * ''PlaybackActor'' to send the next block of data. If no more data is left,
 * playback stops until the temporary buffer on the hard disk is filled again.
 *
 * ''PlaybackActor'' also reacts on messages for pausing and resuming playback.
 * If a line is open, it is directly stopped and started, respectively.
 *
 * @param gateway the gateway object
 * @param ctxFactoryActor the actor for creating playback context objects
 * @param streamFactory a factory for creating stream objects
 * @param minimumBufferLimit the minimum amount of data which must be in the
 * audio buffer before playback can start; this value must correspond to the
 * mark() and reset() operations performed by the audio engine when setting up
 * an mp3 audio stream; the default value should be appropriate, but can be
 * adapted if necessary
 */
class PlaybackActor(gateway: Gateway, val ctxFactoryActor: Actor,
  streamFactory: SourceStreamWrapperFactory,
  minimumBufferLimit: Int = PlaybackActor.MinimumBufferLimit) extends Actor {
  /**
   * Constant for the threshold for position changed event. This actor ensures
   * that in the given time frame (in milliseconds) only a single event of this
   * type if generated.
   */
  private val PositionChangedThreshold = 500L

  /** The logger. */
  private val log = LoggerFactory.getLogger(classOf[PlaybackActor])

  /** A queue with the audio sources to be played.*/
  private val queue = Queue.empty[AudioSource]

  /** The current context. */
  private var context: PlaybackContext = _

  /** The current input stream. */
  private var stream: SourceStreamWrapper = _

  /** The buffer for feeding the line. */
  private var playbackBuffer: Array[Byte] = _

  /** Holds the currently played audio source. */
  private var currentSource: AudioSource = _

  /** The last source that was added. */
  private var latestSource: AudioSource = _

  /** The current position in the audio stream. */
  private var streamPosition = 0L

  /** The current skip position. */
  private var skipPosition = 0L

  /** The last time a position changed event was fired. */
  private var lastPositionChangedTime = 0L

  /** The size of the last written chunk. */
  private var lastChunkSize = 0

  /** The number of bytes written by the line writer actor. */
  private var writtenForLastChunk = 0

  /** A flag whether playback is enabled. */
  private var playbackEnabled = true

  /** A flag whether currently a chunk is played. */
  private var chunkPlaying = false

  /** A flag whether the end of the playlist is reached. */
  private var endOfPlaylist = false

  /** A flag whether already an end of playlist stop event has been fired. */
  private var endOfPlaylistMessage = false

  /**
   * A flag whether a read error occurred. If this flag is set, data is read
   * from the original data stream rather than from the audio stream because
   * the stream is just skipped.
   */
  private var errorStream = false

  /**
   * A flag whether playback has already taken part since the last flush
   * operation.
   */
  private var playbackPerformed = false

  /**
   * A flag whether a request for creating a playback context is pending.
   */
  private var waitForContextCreation = false

  /**
   * The main message loop of this actor.
   */
  def act() {
    var running = true

    while (running) {
      receive {
        case cl: Closeable =>
          running = false
          flushActor()
          cl.close()
          gateway.publish(ActorExited(this))
          log.info(this + " exited.")

        case src: AudioSource =>
          enqueueSource(src)

        case temp: TempFile =>
          enqueueTempFile(temp)

        case ChunkPlayed(written) =>
          handleChunkPlayed(written)

        case StopPlayback =>
          handleStopPlayback()

        case StartPlayback =>
          handleStartPlayback()

        case SkipCurrentSource =>
          skipCurrentSource()

        case PlaylistEnd =>
          endOfPlaylist = true
          playback()

        case SourceReadError(newLength) =>
          handleSourceReadError(newLength)

        case fp: FlushPlayer =>
          flushActor()
          fp.executeFollowAction()

        case CreatePlaybackContextResponse(src, pbctx) =>
          handlePlaybackContextCreated(src, pbctx)
      }
    }
  }

  /**
   * Returns a string for this object. This string contains the name of this
   * actor.
   * @return a string for this object
   */
  override def toString = "PlaybackActor"

  /**
   * Adds the specified audio source to the internal queue of songs to play.
   * If no song is currently played, playback starts.
   * @param src the audio source
   */
  private def enqueueSource(src: AudioSource) {
    queue += src
    latestSource = src
    if (context == null) {
      playback()
    }
  }

  /**
   * Adds the specified temporary file to the buffer. Then checks whether
   * playback can be started.
   * @param temp the temporary file to be added
   */
  private def enqueueTempFile(temp: TempFile) {
    streamFactory.bufferManager += temp
    playback()
  }

  /**
   * Initiates playback.
   */
  private def playback() {
    if (isPlaybackAllowed) {
      playChunk()
    }
  }

  /**
   * Checks whether playback can be initiated.
   */
  private def isPlaybackAllowed: Boolean =
    playbackEnabled && !chunkPlaying && sufficientBufferSize && contextAvailable

  /**
   * Determines the size of the playback buffer. If a buffer is already allocated,
   * it can be used directly. Otherwise, a default size is assumed.
   */
  private def playbackBufferSize: Int =
    if (playbackBuffer != null) playbackBuffer.length
    else PlaybackActor.DefaultBufferSize

  /**
   * Checks whether the temporary buffer contains enough data to read another
   * chunk of data. In order to prevent incomplete reads, playback of a chunk
   * is started only if the buffer contains a certain amount of data.
   */
  private def sufficientBufferSize: Boolean =
    endOfPlaylist || streamFactory.bufferManager.bufferSize >= minimumBufferLimit

  /**
   * Checks whether a playback context is available. If not, it is tried to
   * create one. If this method returns '''true''', a context is available and
   * can be used for playback.
   */
  private def contextAvailable: Boolean =
    if (waitForContextCreation) false
    else if (context != null || errorStream) true
    else {
      setUpPlaybackContext()
      false
    }

  /**
   * Plays a chunk of the audio data.
   */
  private def playChunk() {
    val (ofs, len) = handlePartlyWrittenBuffer()
    val read = if (len > 0) readStream(ofs, len) else 0
    if (read < 0 && ofs == 0) {
      closeCurrentAudioSource()
      playback()
    } else {
      val readFromStream = scala.math.max(read, 0)
      passChunkToLineActor(ofs, readFromStream)
      streamPosition += readFromStream
      chunkPlaying = true
    }
  }

  /**
   * Reads data from the current input stream. If the errorStream flag is set,
   * data is read from the original stream, not from the audio stream. The data
   * read is stored in the playback buffer.
   * @param ofs the offset in the playback buffer
   * @param len the number of bytes to read from the input stream
   * @return the number of bytes that were read
   */
  private def readStream(ofs: Int, len: Int): Int = {
    val is = if (errorStream) stream else context.stream
    var read: Int = 0

    try {
      read = is.read(playbackBuffer, ofs, len)
    } catch {
      // also runtime exceptions have to be handled
      case ex: Exception =>
        val msg = "Error when reading from audio stream for source " + currentSource
        fireErrorEvent(msg, ex, errorStream)
        if (errorStream) {
          playbackEnabled = false
        } else {
          errorStream = true
          skipCurrentSource()
          read = 0
        }
    }
    read
  }

  /**
   * Handles the case that the last chunk was not played completely. In this
   * case, the remaining parts have to be played again, and the number of bytes
   * to read from the input stream has to be adapted.
   * @return a tuple with the offset in the playback buffer and the number of
   * bytes to read from the input stream for the next read operation
   */
  private def handlePartlyWrittenBuffer(): Tuple2[Int, Int] = {
    if (lastChunkSize == 0) {
      (0, playbackBufferSize)
    } else {
      val ofs = scala.math.max(lastChunkSize - writtenForLastChunk, 0)
      if (ofs > 0 && writtenForLastChunk > 0) {
        System.arraycopy(playbackBuffer, writtenForLastChunk, playbackBuffer, 0, ofs)
      }
      (ofs, writtenForLastChunk)
    }
  }

  /**
   * Notifies the line actor to play the current chunk.
   * @param ofs the start position in the playback buffer with newly read data;
   * this is needed to calculate the correct stream position which corresponds
   * to the first byte of the buffer
   * @param len the size of the current chunk
   */
  private def passChunkToLineActor(ofs: Int, len: Int) {
    val line = if (errorStream) null else context.line
    val msg = PlayChunk(line, Arrays.copyOf(playbackBuffer,
      playbackBuffer.length), ofs + len, streamPosition - ofs, skipPosition)
    gateway ! Gateway.ActorLineWrite -> msg
    lastChunkSize = msg.len
  }

  /**
   * Creates the objects required for the playback of a new audio file. If there
   * are no more audio files to play, this method sends a playback stop event
   * because the end of the playlist is reached.
   */
  private def setUpPlaybackContext() {
    if (queue.isEmpty) {
      fireStopEventAtEndOfPlaylist()
    } else {
      preparePlayback()
    }
  }

  /**
   * Prepares the playback of a new audio file and creates the necessary context
   * objects.
   */
  private def preparePlayback() {
    val source = queue.dequeue()
    log.info("Starting playback of {}.", source.uri)
    fireInitialPlaybackStartEvent()
    gateway.publish(PlaybackSourceStart(source))

    skipPosition = source.skip
    errorStream = false
    currentSource = source
    writtenForLastChunk = 0
    lastChunkSize = 0
    lastPositionChangedTime = 0

    try {
      val sourceStream = if (stream != null) stream.currentStream else null
      stream = streamFactory.createStream(sourceStream, source.length)
      waitForContextCreation = true
      ctxFactoryActor ! CreatePlaybackContextRequest(stream, source, this)
    } catch {
      case ex: Exception =>
        handleContextCreationError(ex)
    }

    streamPosition = 0
  }

  /**
   * Handles a message about a newly created playback context. Such messages
   * are sent by the playback context actor as response of
   * ''CreatePlaybackContextRequest'' messages. If the message refers to the
   * current source, playback can actually start.
   * @param src the audio source the message refers to
   * @param pbctx the new playback context
   */
  private def handlePlaybackContextCreated(src: AudioSource,
    pbctx: Option[PlaybackContext]) {
    log.info("Playback context created for source {}", src)
    if (src == currentSource) {
      waitForContextCreation = false
      if (pbctx.isEmpty) {
        handleContextCreationError(
          new IllegalStateException("No playback context could be created!"))
      } else {
        context = pbctx.get
        playbackBuffer = context.createPlaybackBuffer()
        prepareLine()
        playback()
      }
    } else {
      log.warn("Unknown source: " + src + ", expected: " + currentSource)
    }
  }

  /**
   * Prepares the current line to start playback.
   */
  private def prepareLine() {
    context.line.open(context.format)
    context.line.start()
  }

  /**
   * Handles an exception when creating the playback context for a source.
   * @param ex the exception
   */
  private def handleContextCreationError(ex: Throwable) {
    val msg = "Cannot create PlaybackContext for source " + currentSource
    fireErrorEvent(msg, ex, false)
    errorStream = true
    skipPosition = Long.MaxValue
    playbackBuffer = new Array[Byte](PlaybackActor.DefaultBufferSize)
  }

  /**
   * Fires an event indicating a playback error. Some fields are set according
   * to the error state.
   * @param msg the error message
   * @param ex the exception which caused this error
   * @param fatal a flag whether this is a fatal error
   */
  private def fireErrorEvent(msg: String, ex: Throwable, fatal: Boolean) {
    log.error(msg, ex)
    gateway.publish(PlaybackError(msg, ex, fatal, currentSource))
    handleStopPlayback()
  }

  /**
   * Skips the current audio source.
   */
  private def skipCurrentSource() {
    flushLine()
    skipPosition = Long.MaxValue
    errorStream = true // now read from source stream
  }

  /**
   * Handles a message that a read error occurred for the latest source which
   * has been added. In this case the source's length has to be adapted. It
   * has to be distinguished whether the source is already played or not.
   * @param newLength the new length of the latest source
   */
  private def handleSourceReadError(newLength: Long) {
    if (queue.isEmpty) {
      adaptLengthOfCurrentSource(newLength)
    } else {
      adaptLengthOfSourceInQueue(newLength)
    }
  }

  /**
   * Adapts the length of the current source.
   * @param newLength the new length
   */
  private def adaptLengthOfCurrentSource(newLength: Long) {
    stream.changeLength(newLength)
  }

  /**
   * Adapts the length of a source which is still in the queue.
   * @param newLength the new length
   */
  private def adaptLengthOfSourceInQueue(newLength: Long) {
    queue.dequeueFirst(_ eq latestSource)
    queue += latestSource.resize(newLength)
  }

  /**
   * Closes all resources related to the current audio source.
   */
  private def closeCurrentAudioSource() {
    if (currentSource != null) {
      gateway.publish(PlaybackSourceEnd(currentSource,
        skipPosition == Long.MaxValue))
      currentSource = null
    }
    if (context != null) {
      context.close()
      context = null
    }
    playbackBuffer = null
    skipPosition = 0
    errorStream = false
  }

  /**
   * Performs an operation on the current line. If a line is currently open,
   * the function is invoked. Otherwise, this method has no effect.
   * @param f the function to be performed on the line
   */
  private def updateLine(f: SourceDataLine => Unit) {
    if (context != null) {
      f(context.line)
    }
  }

  /**
   * Processes a message that a chunk was played. This method updates some
   * internal fields, notifies listeners, and triggers the playback of the next
   * chunk.
   * @param written the number of bytes that have actually been written for the
   * last chunk
   */
  private def handleChunkPlayed(written: Int): Unit = {
    chunkPlaying = false
    writtenForLastChunk = written
    firePositionChangedEvent()
    playback()
  }

  /**
   * Creates a message about a change in the current audio stream's position.
   * @return the change message
   */
  private def createPositionChangedMessage() =
    PlaybackPositionChanged(streamPosition, context.streamSize,
      stream.currentPosition, currentSource)

  /**
   * Handles a start playback message. Checks whether playback is already
   * active.
   */
  private def handleStartPlayback() {
    if (!playbackEnabled) {
      playbackEnabled = true
      if (!errorStream) {
        updateLine(_.start())
      }
      gateway.publish(PlaybackStarts)
      playback()
    }
  }

  /**
   * Handles a stop playback message. An action is only taken if playback is
   * active.
   */
  private def handleStopPlayback() {
    if (playbackEnabled) {
      playbackEnabled = false
      updateLine(_.stop())
      gateway.publish(PlaybackStops)
    }
  }

  /**
   * Fires a stop event once when the end of the playlist is reached.
   */
  private def fireStopEventAtEndOfPlaylist() {
    if (endOfPlaylist && !endOfPlaylistMessage) {
      endOfPlaylistMessage = true
      gateway.publish(PlaybackStops)
      gateway.publish(PlaylistEnd)
    }
  }

  /**
   * Takes care that a playback start event is fired when audio data is played
   * for the first time after a flush operation.
   */
  private def fireInitialPlaybackStartEvent() {
    if (!playbackPerformed) {
      playbackPerformed = true
      gateway.publish(PlaybackStarts)
    }
  }

  /**
   * Performs a flush on the current line if it is available.
   */
  private def flushLine() {
    updateLine { line =>
      line.stop()
      line.flush()
    }
  }

  /**
   * Closes the current context if it is available.
   */
  private def closeContext() {
    if (context != null) {
      context.close()
      context = null
    }
  }

  /**
   * Performs a flush operation on this actor. This means that the internal state
   * is reset so the actor can be used to play another playlist.
   */
  private def flushActor() {
    log.info("Flush of playback actor.")
    flushLine()
    closeContext()
    queue.clear()
    if (stream != null) {
      stream.closeCurrentStream()
      stream = null
    }
    streamFactory.bufferManager.flush()
    endOfPlaylist = false
    endOfPlaylistMessage = false
    chunkPlaying = false
    playbackEnabled = true
    playbackPerformed = false
    errorStream = false
    waitForContextCreation = false
  }

  /**
   * Fires a position changed event if this is possible. This method also takes
   * the threshold into account.
   */
  private def firePositionChangedEvent() {
    if (context != null) {
      var now = System.currentTimeMillis()
      if (now - lastPositionChangedTime > PositionChangedThreshold) {
        lastPositionChangedTime = now
        gateway.publish(createPositionChangedMessage())
      }
    }
  }
}

/**
 * The companion object of ''PlaybackActor''.
 */
object PlaybackActor {
  /**
   * Constant for the minimum buffer limit. Playback is only possible if the
   * audio buffer contains at least this amount of data.
   */
  val MinimumBufferLimit = 256000

  /**
   * Constant for the default buffer size. This size is used if no playback
   * buffer is available.
   */
  val DefaultBufferSize = 4096
}
