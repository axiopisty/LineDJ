package de.oliver_heger.jplaya.ui.mainwnd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.util.concurrent.atomic.AtomicReference;

import net.sf.jguiraffe.gui.app.Application;
import net.sf.jguiraffe.gui.app.ApplicationContext;
import net.sf.jguiraffe.gui.app.OpenWindowCommand;
import net.sf.jguiraffe.gui.builder.utils.GUISynchronizer;
import net.sf.jguiraffe.gui.builder.utils.MessageOutput;
import net.sf.jguiraffe.gui.builder.window.WindowEvent;
import net.sf.jguiraffe.gui.cmd.Command;
import net.sf.jguiraffe.locators.ClassPathLocator;

import org.apache.commons.configuration.Configuration;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

import de.oliver_heger.jplaya.ui.AudioPlayerClient;
import de.oliver_heger.jplaya.ui.ConfigurationConstants;
import de.oliver_heger.splaya.AudioPlayer;
import de.oliver_heger.splaya.AudioPlayerEvent;
import de.oliver_heger.splaya.AudioPlayerEventType;
import de.oliver_heger.splaya.PlaylistEvent;
import de.oliver_heger.splaya.PlaylistEventType;

/**
 * Test class for {@code MainWndController}.
 *
 * @author Oliver Heger
 * @version $Id: $
 */
public class TestMainWndController extends EasyMockSupport
{
    /** A mock for the playlist model. */
    private PlaylistModel model;

    /** A mock for the playlist table model. */
    private PlaylistTableModel tableModel;

    /** A mock for the action model. */
    private ActionModel actionModel;

    /** A mock for the synchronizer. */
    private GUISynchronizer sync;

    /** A mock for the audio player. */
    private AudioPlayer player;

    /** The controller to be tested. */
    private MainWndController ctrl;

    @Before
    public void setUp() throws Exception
    {
        model = createMock(PlaylistModel.class);
        tableModel = createMock(PlaylistTableModel.class);
        actionModel = createMock(ActionModel.class);
        sync = createMock(GUISynchronizer.class);
        player = createMock(AudioPlayer.class);

        ctrl = new MainWndController();
        ctrl.setPlaylistModel(model);
        ctrl.setActionModel(actionModel);
        ctrl.setPlaylistTableModel(tableModel);
        ctrl.setSynchronizer(sync);
        ctrl.setAudioPlayer(player);
    }

    /**
     * Prepares the GUI synchronizer mock for a single invocation. The mock
     * expects an asynchronous invocation with an arbitrary Runnable. The
     * Runnable is invoked.
     */
    private void expectSync()
    {
        sync.asyncInvoke(EasyMock.anyObject(Runnable.class));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>()
        {
            @Override
            public Object answer() throws Throwable
            {
                Runnable r = (Runnable) EasyMock.getCurrentArguments()[0];
                r.run();
                return null;
            }
        });
    }

    /**
     * Tests the window activated event. We can only check that the event is not
     * touched.
     */
    @Test
    public void testWindowActivated()
    {
        WindowEvent event = createMock(WindowEvent.class);
        replayAll();
        ctrl.windowActivated(event);
        verifyAll();
    }

    /**
     * Tests the window closed event. We can only check that the event is not
     * touched.
     */
    @Test
    public void testWindowClosed()
    {
        WindowEvent event = createMock(WindowEvent.class);
        replayAll();
        ctrl.windowClosed(event);
        verifyAll();
    }

    /**
     * Tests the window closing event. We can only check that the event is not
     * touched.
     */
    @Test
    public void testWindowClosing()
    {
        WindowEvent event = createMock(WindowEvent.class);
        replayAll();
        ctrl.windowClosing(event);
        verifyAll();
    }

    /**
     * Tests the window deactivated event. We can only check that the event is
     * not touched.
     */
    @Test
    public void testWindowDeactivated()
    {
        WindowEvent event = createMock(WindowEvent.class);
        replayAll();
        ctrl.windowDeactivated(event);
        verifyAll();
    }

    /**
     * Tests the window deiconified event. We can only check that the event is
     * not touched.
     */
    @Test
    public void testWindowDeiconified()
    {
        WindowEvent event = createMock(WindowEvent.class);
        replayAll();
        ctrl.windowDeiconified(event);
        verifyAll();
    }

    /**
     * Tests the window iconified event. We can only check that the event is not
     * touched.
     */
    @Test
    public void testWindowIconified()
    {
        WindowEvent event = createMock(WindowEvent.class);
        replayAll();
        ctrl.windowIconified(event);
        verifyAll();
    }

    /**
     * Tests reaction on the window opened event.
     */
    @Test
    public void testWindowOpenedMediaDir()
    {
        WindowEvent event = createMock(WindowEvent.class);
        actionModel.disablePlayerActions();
        replayAll();
        ctrl.windowOpened(event);
        verifyAll();
    }

    /**
     * Tests the bind audio player callback if no media directory is configured.
     */
    @Test
    public void testBindAudioPlayerNoMediaDir()
    {
        Application app = createMock(Application.class);
        Configuration config = createMock(Configuration.class);
        EasyMock.expect(app.getUserConfiguration()).andReturn(config);
        EasyMock.expect(config.getString(ConfigurationConstants.PROP_MEDIA_DIR))
                .andReturn(null);
        actionModel.disablePlayerActions();
        player.addAudioPlayerListener(ctrl);
        player.addPlaylistListener(ctrl);
        replayAll();
        ctrl.setAudioPlayer(null);
        ctrl.setApplication(app);
        ctrl.bindAudioPlayer(player);
        assertSame("Player not stored", player, ctrl.getAudioPlayer());
        verifyAll();
    }

    /**
     * Tests the bind audio player callback if a media directory is configured.
     * In this case the audio player should be started immediately.
     */
    @Test
    public void testBindAudioPlayerMediaDir()
    {
        Application app = createMock(Application.class);
        Configuration config = createMock(Configuration.class);
        final String mediaDir = "R:\\";
        EasyMock.expect(app.getUserConfiguration()).andReturn(config);
        EasyMock.expect(config.getString(ConfigurationConstants.PROP_MEDIA_DIR))
                .andReturn(mediaDir);
        actionModel.disablePlayerActions();
        player.addAudioPlayerListener(ctrl);
        player.addPlaylistListener(ctrl);
        player.readMedium(mediaDir);
        replayAll();
        ctrl.setApplication(app);
        ctrl.bindAudioPlayer(player);
        verifyAll();
    }

    /**
     * Creates a mock for an audio player event.
     *
     * @param type the event type
     * @return the mock for this event
     */
    private AudioPlayerEvent createAudioPlayerEvent(AudioPlayerEventType type)
    {
        AudioPlayerEvent event = EasyMock.createMock(AudioPlayerEvent.class);
        EasyMock.expect(event.getType()).andReturn(type).anyTimes();
        EasyMock.replay(event);
        return event;
    }

    /**
     * Prepares a test for an audio player event handler.
     *
     * @param type the event type
     * @return the mock event
     */
    private AudioPlayerEvent prepareAudioPlayerEventTest(
            AudioPlayerEventType type)
    {
        expectSync();
        AudioPlayerEvent event = createAudioPlayerEvent(type);
        model.handleAudioPlayerEvent(event);
        actionModel.handleAudioPlayerEvent(event);
        replayAll();
        return event;
    }

    /**
     * Tests whether a playlist end event is handled correctly.
     */
    @Test
    public void testPlaylistEndsEvent()
    {
        AudioPlayerEvent event =
                prepareAudioPlayerEventTest(AudioPlayerEventType.PLAYLIST_END);
        ctrl.playlistEnds(event);
        verifyAll();
    }

    /**
     * Tests whether a position changed event is handled correctly.
     */
    @Test
    public void testPositionChangedEvent()
    {
        AudioPlayerEvent event =
                prepareAudioPlayerEventTest(AudioPlayerEventType.POSITION_CHANGED);
        ctrl.positionChanged(event);
        verifyAll();
    }

    /**
     * Tests whether a source end event is handled correctly.
     */
    @Test
    public void testSourceEndEvent()
    {
        AudioPlayerEvent event =
                prepareAudioPlayerEventTest(AudioPlayerEventType.END_SOURCE);
        ctrl.sourceEnds(event);
        verifyAll();
    }

    /**
     * Tests whether a source start event is handled correctly.
     */
    @Test
    public void testSourceStartEvent()
    {
        AudioPlayerEvent event =
                prepareAudioPlayerEventTest(AudioPlayerEventType.START_SOURCE);
        ctrl.sourceStarts(event);
        verifyAll();
    }

    /**
     * Tests whether a playback start event is handled correctly.
     */
    @Test
    public void testPlaybackStartEvent()
    {
        AudioPlayerEvent event =
                prepareAudioPlayerEventTest(AudioPlayerEventType.START_PLAYBACK);
        ctrl.playbackStarts(event);
        verifyAll();
    }

    /**
     * Tests whether a playback stop event is handled correctly.
     */
    @Test
    public void testPlaybackStopEvent()
    {
        AudioPlayerEvent event =
                prepareAudioPlayerEventTest(AudioPlayerEventType.STOP_PLAYBACK);
        ctrl.playbackStops(event);
        verifyAll();
    }

    /**
     * Creates a mock for a playlist event of the specified type.
     *
     * @param type the event type
     * @return the event mock
     */
    private PlaylistEvent createPlaylistEvent(PlaylistEventType type)
    {
        PlaylistEvent event = EasyMock.createMock(PlaylistEvent.class);
        EasyMock.expect(event.getType()).andReturn(type).anyTimes();
        EasyMock.replay(event);
        return event;
    }

    /**
     * Prepares a test for handling a playlist event.
     *
     * @param type the event type
     * @return the mock event
     */
    private PlaylistEvent preparePlaylistEventTest(PlaylistEventType type)
    {
        expectSync();
        PlaylistEvent event = createPlaylistEvent(type);
        model.handlePlaylistEvent(event);
        tableModel.handlePlaylistEvent(event);
        replayAll();
        return event;
    }

    /**
     * Tests whether an event for a newly created playlist is handled correctly.
     */
    @Test
    public void testPlaylistCreatedEvent()
    {
        PlaylistEvent event =
                preparePlaylistEventTest(PlaylistEventType.PLAYLIST_CREATED);
        ctrl.playlistCreated(event);
        verifyAll();
    }

    /**
     * Tests whether an event for an updated playlist is handled correctly.
     */
    @Test
    public void testPlaylistUpdatedEvent()
    {
        PlaylistEvent event =
                preparePlaylistEventTest(PlaylistEventType.PLAYLIST_UPDATED);
        ctrl.playlistUpdated(event);
        verifyAll();
    }

    /**
     * Tests whether playback can be started.
     */
    @Test
    public void testStartPlayback()
    {
        player.startPlayback();
        replayAll();
        ctrl.startPlayback();
        verifyAll();
    }

    /**
     * Tests whether playback can be stopped.
     */
    @Test
    public void testStopPlayback()
    {
        player.stopPlayback();
        replayAll();
        ctrl.stopPlayback();
        verifyAll();
    }

    /**
     * Tests whether we can skip to the next song in the playlist.
     */
    @Test
    public void testMoveForward()
    {
        player.moveForward();
        replayAll();
        ctrl.moveForward();
        verifyAll();
    }

    /**
     * Tests whether we can go backwards in the playlist.
     */
    @Test
    public void testMoveBackward()
    {
        actionModel.disablePlayerActions();
        player.moveBackward();
        replayAll();
        ctrl.moveBackward();
        verifyAll();
    }

    /**
     * Tests whether a specific index in the playlist can be selected.
     */
    @Test
    public void testMoveTo()
    {
        final int index = 42;
        actionModel.disablePlayerActions();
        player.moveToSource(index);
        replayAll();
        ctrl.moveTo(index);
        verifyAll();
    }

    /**
     * Tests a shutdown notification.
     */
    @Test
    public void testShutdown()
    {
        actionModel.disablePlayerActions();
        player.shutdown();
        replayAll();
        ctrl.shutdown();
        verifyAll();
    }

    /**
     * Tests whether a non-fatal playback error can be handled.
     */
    @Test
    public void testPlaybackErrorNonFatal()
    {
        Application app = createMock(Application.class);
        AudioPlayerEvent event =
                createAudioPlayerEvent(AudioPlayerEventType.EXCEPTION);
        app.execute(EasyMock.anyObject(Command.class));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>()
        {
            @Override
            public Object answer() throws Throwable
            {
                OpenWindowCommand cmd =
                        (OpenWindowCommand) EasyMock.getCurrentArguments()[0];
                ClassPathLocator cploc = (ClassPathLocator) cmd.getLocator();
                assertEquals("Wrong resource name", "playbackerror.jelly",
                        cploc.getResourceName());
                assertNotNull("No class loader set", cploc.getClassLoader());
                return null;
            }
        });
        replayAll();
        ctrl.setApplication(app);
        ctrl.playbackError(event);
        verifyAll();
    }

    /**
     * Tests whether a fatal playback error can be handled.
     */
    @Test
    public void testPlaybackErrorFatal()
    {
        Application app = createMock(Application.class);
        ApplicationContext ctx = createMock(ApplicationContext.class);
        AudioPlayerEvent event =
                createAudioPlayerEvent(AudioPlayerEventType.FATAL_EXCEPTION);
        EasyMock.expect(app.getApplicationContext()).andReturn(ctx);
        EasyMock.expect(
                ctx.messageBox("err_playback_msg", "err_playback_title",
                        MessageOutput.MESSAGE_ERROR, MessageOutput.BTN_OK))
                .andReturn(0);
        app.shutdown();
        expectSync();
        replayAll();
        ctrl.setApplication(app);
        ctrl.playbackError(event);
        verifyAll();
    }

    /**
     * Tests whether a player shutdown event is processed correctly.
     */
    @Test
    public void testPlayerShutdownEvent()
    {
        Application app = createMock(Application.class);
        AudioPlayerEvent event =
                createAudioPlayerEvent(AudioPlayerEventType.PLAYER_SHUTDOWN);
        app.shutdown();
        replayAll();
        ctrl.setApplication(app);
        ctrl.playerShutdown(event);
        verifyAll();
    }

    /**
     * Tests whether the reference to the audio player client is correctly
     * initialized.
     */
    @Test
    public void testSetPlayerClientReference()
    {
        AtomicReference<AudioPlayerClient> ref =
                new AtomicReference<AudioPlayerClient>();
        replayAll();
        ctrl.setPlayerClientReference(ref);
        assertSame("Reference not initialized", ctrl, ref.get());
    }
}
