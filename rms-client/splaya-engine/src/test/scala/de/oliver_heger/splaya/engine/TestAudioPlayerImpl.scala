package de.oliver_heger.splaya.engine

import org.easymock.EasyMock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import org.scalatest.mock.EasyMockSugar

import de.oliver_heger.splaya.engine.msg.AddAudioPlayerEventListener
import de.oliver_heger.splaya.engine.msg.AddPlaylistEventListener
import de.oliver_heger.splaya.engine.msg.Exit
import de.oliver_heger.splaya.engine.msg.FlushPlayer
import de.oliver_heger.splaya.engine.msg.Gateway
import de.oliver_heger.splaya.engine.msg.RemoveAudioPlayerEventListener
import de.oliver_heger.splaya.engine.msg.RemovePlaylistEventListener
import de.oliver_heger.splaya.engine.msg.SkipCurrentSource
import de.oliver_heger.splaya.engine.msg.StartPlayback
import de.oliver_heger.splaya.engine.msg.StopPlayback
import de.oliver_heger.splaya.engine.msg.TimeAction
import de.oliver_heger.splaya.AudioPlayerListener
import de.oliver_heger.splaya.PlaylistListener
import de.oliver_heger.tsthlp.ActorTestImpl
import de.oliver_heger.tsthlp.QueuingActor

/**
 * Test class for ''AudioPlayerImpl''.
 */
class TestAudioPlayerImpl extends JUnitSuite with EasyMockSugar {
  /** A mock for the playlist controller. */
  private var plCtrl: PlaylistController = _

  /** A mock for the playback actor. */
  private var playbackActor: QueuingActor = _

  /** A mock for the source reader actor. */
  private var readerActor: QueuingActor = _

  /** A mock for the timing actor. */
  private var timingActor: QueuingActor = _

  /** A mock for the event translation actor. */
  private var eventActor: ActorTestImpl = _

  /** The gateway object. */
  private var gateway: Gateway = _

  /** The player to be tested. */
  private var player: AudioPlayerImpl = _

  @Before def setUp() {
    plCtrl = mock[PlaylistController]
    playbackActor = new QueuingActor
    playbackActor.start()
    gateway = new Gateway
    gateway += Gateway.ActorPlayback -> playbackActor
    readerActor = new QueuingActor
    readerActor.start()
    gateway += Gateway.ActorSourceRead -> readerActor
    gateway.start()
    timingActor = new QueuingActor
    timingActor.start()
    eventActor = new ActorTestImpl
    player = new AudioPlayerImpl(gateway, plCtrl, timingActor, eventActor)
  }

  @After def tearDown() {
    playbackActor.shutdown()
    readerActor.shutdown()
    timingActor.shutdown()
    gateway.shutdown()
  }

  /**
   * Helper method for testing whether the actors do not have any more messages
   * to process.
   */
  private def ensureActorsNoMessages() {
    playbackActor.ensureNoMessages()
    readerActor.ensureNoMessages()
  }

  /**
   * Tests whether playback can be started.
   */
  @Test def testStartPlayback() {
    EasyMock.replay(plCtrl)
    player.startPlayback()
    playbackActor.expectMessage(StartPlayback)
    ensureActorsNoMessages()
  }

  /**
   * Tests whether playback can be stopped.
   */
  @Test def testStopPlayback() {
    EasyMock.replay(plCtrl)
    player.stopPlayback()
    playbackActor.expectMessage(StopPlayback)
    ensureActorsNoMessages()
  }

  /**
   * Tests whether the player can move forward to the next song in the playlist.
   */
  @Test def testMoveForward() {
    EasyMock.replay(plCtrl)
    player.moveForward()
    playbackActor.expectMessage(SkipCurrentSource)
    ensureActorsNoMessages()
  }

  /**
   * Tests whether the player can jump to a specific audio source in the
   * playlist.
   */
  @Test def testMoveToSource() {
    val idx = 42
    plCtrl.moveToSourceAt(idx)
    whenExecuting(plCtrl) {
      player.moveToSource(idx)
      extractFlushMessage(readerActor)
    }
    ensureActorsNoMessages()
  }

  /**
   * Tests whether a medium can be read.
   */
  @Test def testReadMedium() {
    val uri = "/data/mymusic/"
    plCtrl.readMedium(uri)
    whenExecuting(plCtrl) {
      player.readMedium(uri)
      extractFlushMessage(readerActor)
    }
    ensureActorsNoMessages()
  }

  /**
   * Helper method for testing that all test actors have received an Exit
   * message.
   */
  private def checkActorsExit() {
    readerActor.expectMessage(Exit)
    playbackActor.expectMessage(Exit)
    timingActor.expectMessage(Exit)
    eventActor.ensureNoMessages()
  }

  /**
   * Tests whether a shutdown of the player works correctly.
   */
  @Test def testShutdown() {
    val lineActor = new QueuingActor
    lineActor.start()
    gateway += Gateway.ActorLineWrite -> lineActor
    plCtrl.shutdown()
    whenExecuting(plCtrl) {
      player.shutdown()
    }
    checkActorsExit()
    lineActor.expectMessage(Exit)
    ensureActorsNoMessages()
  }

  /**
   * Extracts a ''TimeAction'' message from the mock timing actor or fails.
   * @return the ''TimeAction'' message
   */
  private def extractTimeAction(): TimeAction =
    timingActor.nextMessage() match {
      case ta: TimeAction => ta
      case _ => fail("Unexpected message!")
    }

  /**
   * Checks whether the specified actor received a flush message. If so, its
   * follow action is executed.
   * @param act the queuing actor
   */
  private def extractFlushMessage(act: QueuingActor) {
    act.nextMessage() match {
      case fp: FlushPlayer =>
        fp.executeFollowAction()
      case other => fail("Unexpected message: " + other)
    }
  }

  /**
   * Tests a move backward operation if the current audio source has to be
   * played again.
   */
  @Test def testMoveBackwardReplay() {
    plCtrl.moveToSourceRelative(0)
    whenExecuting(plCtrl) {
      player.moveBackward()
      extractFlushMessage(readerActor)
      extractTimeAction().f(5000)
    }
    ensureActorsNoMessages()
  }

  /**
   * Tests a move backward operation if the previous audio source has to be
   * played.
   */
  @Test def testMoveBackwardPrevious() {
    plCtrl.moveToSourceRelative(-1)
    whenExecuting(plCtrl) {
      player.moveBackward()
      extractFlushMessage(readerActor)
      extractTimeAction().f(4999)
    }
    ensureActorsNoMessages()
  }

  /**
   * Tests whether an audio player listener can be added.
   */
  @Test def testAddAudioPlayerListener() {
    val listener = mock[AudioPlayerListener]
    player.addAudioPlayerListener(listener)
    eventActor.expectMessage(AddAudioPlayerEventListener(listener))
  }

  /**
   * Tests whether an audio player listener can be removed.
   */
  @Test def testRemoveAudioPlayerListener() {
    val listener = mock[AudioPlayerListener]
    player.removeAudioPlayerListener(listener)
    eventActor.expectMessage(RemoveAudioPlayerEventListener(listener))
  }

  /**
   * Tests whether a playlist listener can be added.
   */
  @Test def testAddPlaylistListener() {
    val listener = mock[PlaylistListener]
    player.addPlaylistListener(listener)
    eventActor.expectMessage(AddPlaylistEventListener(listener))
  }

  /**
   * Tests whether a playlist listener can be removed.
   */
  @Test def testRemovePlaylistListener() {
    val listener = mock[PlaylistListener]
    player.removePlaylistListener(listener)
    eventActor.expectMessage(RemovePlaylistEventListener(listener))
  }

  /**
   * Tests whether a listener actor can be registered.
   */
  @Test def testListenerActor() {
    val listener = new QueuingActor
    listener.start()
    player.addActorListener(listener)
    val msg = "someMessage"
    gateway.publish(msg)
    listener.expectMessage(msg)
    player.removeActorListener(listener)
    gateway.publish("someOtherMessage")
    listener.ensureNoMessages()
    listener.shutdown()
  }
}
