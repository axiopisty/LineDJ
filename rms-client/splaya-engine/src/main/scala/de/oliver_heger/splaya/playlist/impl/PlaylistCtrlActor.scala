package de.oliver_heger.splaya.playlist.impl

import java.io.Closeable
import java.io.IOException

import scala.actors.Actor
import scala.collection.mutable.ListBuffer
import scala.xml.Elem

import org.apache.commons.lang3.text.translate.NumericEntityEscaper
import org.apache.commons.lang3.StringEscapeUtils

import de.oliver_heger.splaya.engine.msg.ActorExited
import de.oliver_heger.splaya.engine.msg.AddSourceStream
import de.oliver_heger.splaya.engine.msg.Gateway
import de.oliver_heger.splaya.fs.FSService
import de.oliver_heger.splaya.osgiutil.ServiceWrapper.convertToOption
import de.oliver_heger.splaya.osgiutil.ServiceWrapper
import de.oliver_heger.splaya.playlist.PlaylistFileStore
import de.oliver_heger.splaya.AudioSource
import de.oliver_heger.splaya.PlaybackPositionChanged
import de.oliver_heger.splaya.PlaybackSourceEnd
import de.oliver_heger.splaya.PlaybackSourceStart
import de.oliver_heger.splaya.PlaybackTimeChanged
import de.oliver_heger.splaya.PlaylistData
import de.oliver_heger.splaya.PlaylistEnd
import de.oliver_heger.splaya.PlaylistSettings

/**
 * An actor implementing the major part of the functionality required by a
 * [[de.oliver_heger.splaya.playlist.PlaylistController]] implementation.
 *
 * This actor manages the current playlist of the audio player. It uses a
 * [[de.oliver_heger.splaya.fs.FSService]] object to determine
 * the audio sources available on the current medium. Based on this list a
 * specific playlist is generated with the help of an actor managing the
 * available [[de.oliver_heger.splaya.playlist.playlist.PlaylistGenerator]]
 * objects. This playlist is then communicated to the audio engine in terms of
 * [[de.oliver_heger.splaya.engine.AddSourceStream]] messages sent to the actor
 * responsible for audio streaming. It lies also in the responsibility of this
 * actor to persist the current state of the playlist, so that playback can be
 * interrupted and resumed later. There is an auto-save functionality which
 * causes the playlist to be saved automatically after a configurable number of
 * played audio sources.
 *
 * A playlist can be associated with playlist settings - an XML document with
 * meta information like a name, a description, and a default order mode.
 * Playlist settings are loaded from the ''PlaylistFileStore''; they are named
 * by the playlist ID. If no settings are defined here, this actor searches in
 * the root of the source medium for a file named `playlist.settings`. If it
 * exists, it is loaded and interpreted as playlist settings.
 *
 * @param gateway the gateway object
 * @param sourceActor the actor to which the playlist has to be communicated
 * @param fsService the ''FSService'' for scanning the source medium
 * @param store the ''PlaylistFileStore'' for persisting playlist data
 * @param playlistCreationActor the actor for generating a new playlist
 * @param extensions a set with file extensions of supported audio files
 * @param autoSaveInterval the number of audio sources after which the
 * current playlist is saved (auto-save)
 */
class PlaylistCtrlActor(gateway: Gateway, sourceActor: Actor,
  fsService: ServiceWrapper[FSService], store: PlaylistFileStore,
  playlistCreationActor: Actor, extensions: Set[String],
  autoSaveInterval: Int = 3) extends Actor {
  /** The encoder for playlist items. */
  private final val Encoder = StringEscapeUtils.ESCAPE_XML.`with`(
        NumericEntityEscaper.between(0x7f, Integer.MAX_VALUE))

  /** A sequence with the current playlist. */
  private var playlist: Seq[String] = List.empty

  /** The ID of the current playlist. */
  private var playlistID: String = _

  /** The URI of the current source medium. */
  private var currentSourceMedium: String = _

  /** The current index in the playlist. */
  private var currentIndex: Int = _

  /** The current position in the current audio source. */
  private var currentPos: Long = _

  /** The current time in the current audio source. */
  private var currentTime: Long = _

  /** A counter for implementing the auto-save feature. */
  private var autoSaveCount = 0

  /** A flag whether the playlist has been played completely. */
  private var playlistComplete: Boolean = false

  def act() {
    var running = true

    while (running) {
      receive {
        case cl: Closeable =>
          cl.close()
          running = false
          gateway.publish(ActorExited(this))

        case ReadMedium(uri) =>
          handleReadMedium(uri)

        case resp: PlaylistGenerated =>
          handlePlaylistGenerated(resp)

        case MoveTo(idx) =>
          handleMoveTo(idx)

        case MoveRelative(delta) =>
          handleMoveRelative(delta)

        case SavePlaylist =>
          savePlaylist()

        case PlaybackSourceStart(source) =>
          handlePlaybackSourceStart(source)

        case PlaybackSourceEnd(source, _) =>
          handlePlaybackSourceEnd(source)

        case PlaybackPositionChanged(pos, _, _, _) =>
          currentPos = pos

        case PlaybackTimeChanged(time) =>
          currentTime = time

        case _ => // ignore other messages
      }
    }
  }

  /**
   * Returns a string representation for this actor. This implementation
   * returns the name of this actor.
   * @return a string for this actor
   */
  override def toString = "PlaylistCtrlActor"

  /**
   * Creates the XML for persisting the current playlist. This method is
   * called in reaction on a ''SavePlaylist'' event.
   * @param curIndex the current index in the playlist
   * @param curPos the current position in the current file
   * @param curTime the current time in the current file
   * @param list a sequence with the song names comprising the playlist
   * @return the XML for the current playlist
   */
  protected[impl] def createPlaylistXML(curIndex: Int, curPos: Long,
      curTime: Long, list: Seq[String]): Elem =
    <configuration>
      <current>
        <index>{ curIndex }</index>
        <position>{ curPos }</position>
        <time>{ curTime }</time>
      </current>
      <list>
        {
          for (uri <- list) yield <file name={ Encoder.translate(uri) }>
          </file>
        }
      </list>
    </configuration>

  /**
   * Extracts the playlist from an existing persistent playlist file. This
   * method is called when a playlist in XML form has to be processed.
   * @param playlistData the XML structure representing the playlist
   * @return the extracted playlist or an empty sequence if there is none
   */
  protected[impl] def readPersistentPlaylist(playlistData: Elem): Seq[String] = {
    def decode(s: String): String =
      StringEscapeUtils.unescapeXml(s)

    val pl = ListBuffer.empty[String]

    val current = playlistData \ "current" \ "file" \ "@name"
    if (!current.isEmpty) {
      pl += decode(current.text)
    }

    val files = playlistData \ "list" \ "file"
    for (f <- files) {
      pl += decode((f \ "@name").text)
    }

    pl.toList
  }

  /**
   * Sends the current playlist to the source read actor.
   * @param mediumURI the URI of the source medium
   * @param startIdx the start index
   * @param initSkipPos the initial skip position
   * @param initSkipTime the initial skip time
   */
  private def sendPlaylist(startIdx: Int, initSkipPos: Long = 0,
    initSkipTime: Long = 0) {
    currentIndex = startIdx
    val pl = playlist.drop(startIdx)
    if (!pl.isEmpty) {
      sourceActor ! AddSourceStream(currentSourceMedium, pl.head, startIdx,
        initSkipPos, initSkipTime)
      var idx = startIdx + 1
      for (uri <- pl.tail) {
        sourceActor ! AddSourceStream(currentSourceMedium, uri, idx, 0, 0)
        idx += 1
      }
      sourceActor ! PlaylistEnd
    }
  }

  /**
   * Reads the specified medium and constructs a playlist (either based on a
   * persistent playlist file or by using the ''PlaylistGenerator''). The new
   * playlist is sent to the source actor.
   * @param uri the URI of the medium to be read
   */
  private def handleReadMedium(uri: String) {
    val optList = fsService map(_.scan(uri, extensions))
    val list = optList.getOrElse(List.empty)
    playlistID = store.calculatePlaylistID(list)
    val playlistData = store.loadPlaylist(playlistID)
    val settings = readPlaylistSettings(uri)

    playlist = readPersistentPlaylist(playlistData)
    if (playlist.isEmpty) {
      constructPlaylist(list, settings)
    } else {
      setUpExistingPlaylist(uri, playlistData.get)
      gateway.publish(createPlaylistData(settings))
    }
  }

  /**
   * Processes a notification about a newly created playlist. The songs of the
   * playlist are passed to the source actor. A message about the new playlist
   * is sent out.
   * @param resp the response of the generate playlist request
   */
  private def handlePlaylistGenerated(resp: PlaylistGenerated) {
    playlist = resp.songs
    currentSourceMedium = resp.settings.mediumURI
    sendPlaylist(0)
    gateway.publish(createPlaylistData(resp.settings))
  }

  /**
   * Handles a message to move the current index to an absolute position. If
   * the position is invalid, the message is ignored.
   * @param idx the new index
   */
  private def handleMoveTo(idx: Int) {
    if (idx >= 0 && idx < playlist.size) {
      currentIndex = idx
      sendPlaylist(idx)
    }
  }

  /**
   * Handles a message to move the current index by a given delta. This
   * implementation ensures that the new index is valid. If there is no
   * playlist at all, this message is ignored.
   * @param delta the delta
   */
  private def handleMoveRelative(delta: Int) {
    if (!playlist.isEmpty) {
      currentIndex = scala.math.min(playlist.size - 1,
        scala.math.max(0, currentIndex + delta))
      sendPlaylist(currentIndex)
    }
  }

  /**
   * Handles a message that playback of a new audio source has started.
   * @param source the audio source
   */
  private def handlePlaybackSourceStart(source: AudioSource) {
    playlistComplete = false
    currentIndex = source.index
  }

  /**
   * Handles a message that a source has been played completely. This method
   * also is responsible for the auto save feature.
   * @param source the audio source
   */
  private def handlePlaybackSourceEnd(source: AudioSource) {
    currentPos = 0
    currentTime = 0
    playlistComplete = source.index >= playlist.size - 1
    checkAutoSave()
  }

  /**
   * Extracts the playlist from a persistent playlist file. This method reads
   * all elements referring to audio sources in the playlist. It can also deal
   * with the legacy format which contains another audio source in the section
   * for the current song.
   * @param playlistData the persistent playlist data
   * @return the extracted playlist or an empty sequence if there is none
   */
  private def readPersistentPlaylist(playlistData: Option[Elem]): Seq[String] = {
    val pl = playlistData map { readPersistentPlaylist(_) }
    pl.getOrElse(List.empty)
  }

  /**
   * This method is called if a persistent playlist was found. It obtains the
   * current index and skip positions and sends the playlist to the source
   * actor.
   * @param mediumURI the URI of the source medium
   * @param playlistData the XML root node of the persistent playlist
   */
  private def setUpExistingPlaylist(mediumURI: String, playlistData: Elem) {
    import PlaylistCtrlActor._
    val current = playlistData \ ElemCurrent
    val skipPos = longValue(current \ ElemPosition)
    val skipTime = longValue(current \ ElemTime)
    val index = longValue(current \ ElemIndex).toInt
    currentSourceMedium = mediumURI
    sendPlaylist(index, skipPos, skipTime)
  }

  /**
   * Obtains playlist setting information. If the store does not have a settings
   * document, we search for a settings file on the source medium. If this
   * fails, too, a default settings object is returned.
   * @param mediumURI the URI of the source medium
   * @return an object with playlist settings information
   */
  private def readPlaylistSettings(mediumURI: String): PlaylistSettingsData = {
    var optSettingsData = store.loadSettings(playlistID)
    if (optSettingsData.isEmpty) {
      optSettingsData = readMediumPlaylistSettings(mediumURI)
    }
    val settingsData = optSettingsData map { extractPlaylistSettings(mediumURI, _) }
    settingsData.getOrElse(PlaylistCtrlActor.dummyPlaylistSettingsData(mediumURI))
  }

  /**
   * Tries to load a file with playlist settings from the source medium. This
   * method is called if no playlist settings are found in the playlist store.
   * @param mediumURI the URI of the source medium
   * @return an ''Option'' object with the XML of the loaded playlist settings
   */
  private def readMediumPlaylistSettings(mediumURI: String): Option[xml.Elem] =
    loadXML { () =>
      fsService map (_.resolve(mediumURI,
        PlaylistCtrlActor.MediumSettingsFile)) match {
          case Some(source) =>
            source.openStream()
          case None =>
            // this is very unlikely
            throw new IOException("No FS service available.")
        }
    }

  /**
   * Extracts a data object with playlist settings from an XML representation.
   * @param mediumURI the URI of the source medium
   * @param elem the root XML element
   * @return the data object with playlist settings
   */
  private def extractPlaylistSettings(mediumURI: String, elem: xml.NodeSeq):
    PlaylistSettingsData = {
    import PlaylistCtrlActor._
    val elemOrder = elem \ ElemOrder
    PlaylistSettingsData(name = (elem \ ElemName).text,
      description = (elem \ ElemDesc).text,
      orderMode = (elemOrder \ ElemOrderMode).text,
      orderParams = elemOrder \ ElemOrderParams \ "_",
      mediumURI = mediumURI)
  }

  /**
   * Constructs a new playlist based on the content of the source medium. This
   * method is called if no persistent playlist was found. In this case the
   * ''PlaylistGenerator'' is used to setup a new playlist. The newly created
   * playlist is sent to the source reader actor.
   * @param pl the sequence with the audio sources found on the medium
   * @param settings an object with playlist settings
   */
  private def constructPlaylist(pl: Seq[String], settings: PlaylistSettingsData) {
    playlistCreationActor ! GeneratePlaylist(songs = pl, sender = this,
      settings = settings)
  }

  /**
   * Saves the current playlist including information about the current
   * position. If there is no current playlist, this method has no effect.
   */
  private def savePlaylist() {
    if (playlistID != null) {
      val xml = if (playlistComplete) PlaylistCtrlActor.EmptyPlaylist
      else createPersistentPlaylist
      store.savePlaylist(playlistID, xml)
    }
  }

  /**
   * Constructs the XML representation of the current playlist containing all
   * meta data available. This method delegates to ''createPlaylistXML''
   * passing in the current playlist data.
   * @return the XML representation of the current playlist
   */
  private def createPersistentPlaylist: Elem =
    createPlaylistXML(currentIndex, currentPos, currentTime, playlist)

  /**
   * Performs an auto save if necessary.
   */
  private def checkAutoSave() {
    autoSaveCount += 1
    if (autoSaveCount >= autoSaveInterval) {
      savePlaylist()
      autoSaveCount = 0
    }
  }

  /**
   * Creates a ''PlaylistData'' object with information about the current
   * playlist.
   * @param settingsData a data object with meta data about the playlist
   * @return the new ''PlaylistData'' object
   */
  private def createPlaylistData(settingsData: PlaylistSettings): PlaylistData =
    PlaylistDataImpl(settings = settingsData, playlist = playlist,
      startIndex = currentIndex)
}

/**
 * The companion object for ''PlaylistCtrlActor''.
 */
private object PlaylistCtrlActor {
  /** An XML element representing an empty or completely played playlist. */
  private[impl] val EmptyPlaylist = <configuration/>

  /** Constant for an empty name or description of a playlist. */
  private val EmptyName = ""

  /** Constant for the current XML element. */
  private val ElemCurrent = "current"

  /** Constant for the file XML element. */
  private val ElemFile = "file"

  /** Constant for the current index XML element. */
  private val ElemIndex = "index"

  /** Constant for the current position XML element. */
  private val ElemPosition = "position"

  /** Constant for the current time XML element. */
  private val ElemTime = "time"

  /** Constant for the settings playlist name XML element. */
  private val ElemName = "name"

  /** Constant for the settings playlist description XML element. */
  private val ElemDesc = "description"

  /** Constant for the settings playlist order XML element. */
  private val ElemOrder = "order"

  /** Constant for the order mode XML element. */
  private val ElemOrderMode = "mode"

  /** Constant for the order parameters XML element. */
  private val ElemOrderParams = "params"

  /** Constant for the file name attribute. */
  private val AttrName = "@name"

  /** Constant for the name of the playlist settings file on the medium. */
  private val MediumSettingsFile = "playlist.settings"

  /**
   * Returns the value of the given XML element as Long. If there is no value,
   * result is 0.
   */
  private def longValue(elem: xml.NodeSeq): Long =
    if (elem.isEmpty) 0 else elem.text.toLong

  /**
   * Creates a dummy ''PlaylistSettingsData'' object that is used if no real
   * settings can be found.
   * @param mediumURI the URI of the source medium
   * @return the dummy ''PlaylistSettingsData'' object
   */
  private def dummyPlaylistSettingsData(mediumURI: String) =
    PlaylistSettingsData(PlaylistCtrlActor.EmptyName,
      PlaylistCtrlActor.EmptyName, PlaylistCtrlActor.EmptyName,
      xml.NodeSeq.Empty, mediumURI)
}

/**
 * A data class representing settings for a playlist. This class holds the data
 * extracted from a playlist settings XML document.
 *
 * @param name the name of the playlist
 * @param description a description of the playlist
 * @param orderMode the order mode
 * @param orderParams additional parameters for ordering the playlist
 */
private case class PlaylistSettingsData(name: String, description: String,
  orderMode: String, orderParams: xml.NodeSeq, mediumURI: String)
  extends PlaylistSettings

/**
 * A message which causes the ''PlaylistCtrlActor'' to read the medium with the
 * specified URI. A corresponding playlist will be constructed.
 * @param uri the URI of the root directory to be scanned
 */
private case class ReadMedium(uri: String)

/**
 * A message which causes the ''PlaylistCtrlActor'' to set the current index to
 * the given position. If the index is valid, the new playlist is sent to the
 * source reader actor.
 * @param index the new index in the playlist
 */
private case class MoveTo(index: Int)

/**
 * A message which causes the ''PlaylistCtrlActor'' to move the current index
 * by a relative delta. It is guaranteed that the new index is valid.
 */
private case class MoveRelative(delta: Int)

/**
 * A message which causes the ''PlaylistCtrlActor'' to save the current state of
 * its playlist. This is done using the ''PlaylistFileStore''.
 */
private case object SavePlaylist
