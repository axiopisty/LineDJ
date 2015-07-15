package de.oliver_heger.jplaya.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.persistence.EntityManagerFactory;

import net.sf.jguiraffe.di.BeanContext;
import net.sf.jguiraffe.gui.app.Application;
import net.sf.jguiraffe.gui.builder.window.Window;

import org.apache.commons.lang3.concurrent.ConcurrentInitializer;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.junit.Before;
import org.junit.Test;

import scala.actors.Actor;
import de.oliver_heger.mediastore.localstore.MediaStore;
import de.oliver_heger.mediastore.localstore.OAuthTokenStore;
import de.oliver_heger.mediastore.storelistener.StoreListenerActor;

/**
 * A test class for the global bean definition file of the application.
 *
 * @author Oliver Heger
 * @version $Id: $
 */
public class TestAppBeans
{
    /** The application to be tested. */
    private MainTestImpl app;

    @Before
    public void setUp() throws Exception
    {
        app = new MainTestImpl();
        Application.startup(app, new String[0]);
    }

    /**
     * A convenience method for querying the bean context of the test
     * application.
     *
     * @return the bean context
     */
    private BeanContext getBeanContext()
    {
        return app.getApplicationContext().getBeanContext();
    }

    /**
     * Tests whether the application context was created.
     */
    @Test
    public void testAppCtx()
    {
        assertNotNull("No application context", app.getApplicationContext());
        assertNotNull("No bean context", getBeanContext());
    }

    /**
     * Tests whether a correct initializer bean for the EMF is defined.
     */
    @Test
    public void testBeanEMFInitializer()
    {
        ConcurrentInitializer<?> init =
                (ConcurrentInitializer<?>) getBeanContext().getBean(
                        "emfInitializer");
        Object emf = ConcurrentUtils.initializeUnchecked(init);
        assertTrue("Wrong initialized object: " + emf,
                emf instanceof EntityManagerFactory);
    }

    /**
     * Tests whether the bean for the token store can be queried.
     */
    @Test
    public void testBeanTokenStore()
    {
        OAuthTokenStore tokenStore =
                getBeanContext().getBean(OAuthTokenStore.class);
        assertNotNull("No token store", tokenStore);
    }

    /**
     * Tests whether the bean for the media store has been defined correctly.
     */
    @Test
    public void testBeanMediaStore()
    {
        MediaStore store = getBeanContext().getBean(MediaStore.class);
        assertNotNull("No media store", store);
    }

    /**
     * Tests whether the store listener can be obtained.
     */
    @Test
    public void testBeanStoreListener()
    {
        StoreListenerActor listener =
                (StoreListenerActor) getBeanContext().getBean(
                        "storeListenerActor");
        assertNotNull("No store listener", listener);
        assertSame("Wrong media store",
                getBeanContext().getBean(MediaStore.class), listener.store());
    }

    /**
     * Tests whether the reference to the audio player client can be obtained.
     */
    @Test
    public void testBeanAudioPlayerClientRef()
    {
        AtomicReference<?> ref =
                (AtomicReference<?>) getBeanContext().getBean(
                        Main.BEAN_PLAYER_CLIENT_REF);
        assertNotNull("No audio player client reference", ref);
        assertSame("Multiple instances", ref,
                getBeanContext().getBean(Main.BEAN_PLAYER_CLIENT_REF));
    }

    /**
     * Tests whether a collection with event listener actors can be obtained.
     */
    @Test
    public void testFetchAudioPlayerListenerActors()
    {
        List<Actor> actors = app.fetchAudioPlayerListenerActors();
        assertEquals("Wrong number of elements", 1, actors.size());
        assertTrue("Wrong element at 1",
                actors.get(0) instanceof StoreListenerActor);
    }

    private static class MainTestImpl extends Main
    {
        @Override
        protected void showMainWindow(Window window)
        {
            // ignore this call
        }
    }
}
