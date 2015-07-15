package de.oliver_heger.mediastore.client;

import junit.framework.Test;
import junit.framework.TestCase;

import com.google.gwt.junit.tools.GWTTestSuite;

import de.oliver_heger.mediastore.client.pageman.impl.DockLayoutPageViewTestGwt;
import de.oliver_heger.mediastore.client.pageman.impl.PageManagerImplTestGwt;
import de.oliver_heger.mediastore.client.pages.PagesTestGwt;
import de.oliver_heger.mediastore.client.pages.detail.AlbumDetailsPageTestGwt;
import de.oliver_heger.mediastore.client.pages.detail.ArtistDetailsPageTestGwt;
import de.oliver_heger.mediastore.client.pages.detail.ArtistDetailsTableTestGwt;
import de.oliver_heger.mediastore.client.pages.detail.SongDetailsPageTestGwt;
import de.oliver_heger.mediastore.client.pages.detail.SongDetailsTableTestGwt;
import de.oliver_heger.mediastore.client.pages.detail.SynonymEditorTestGwt;
import de.oliver_heger.mediastore.client.pages.overview.AlbumOverviewPageTestGwt;
import de.oliver_heger.mediastore.client.pages.overview.ArtistOverviewPageTestGwt;
import de.oliver_heger.mediastore.client.pages.overview.OpenPageSingleElementHandlerTestGwt;
import de.oliver_heger.mediastore.client.pages.overview.OverviewPageTestGwt;
import de.oliver_heger.mediastore.client.pages.overview.RemoveControllerDlgTestGwt;
import de.oliver_heger.mediastore.client.pages.overview.SongOverviewPageTestGwt;

/**
 * <p>The main test suite class for all GWT tests.</p>
 *
 * @author Oliver Heger
 * @version $Id: $
 */
public class GwtTestSuite extends TestCase
{
    public static Test suite()
    {
        GWTTestSuite suite = new GWTTestSuite("Main Test suite of RemoteMediaStore project");

        suite.addTestSuite(DisplayErrorPanelTestGwt.class);
        suite.addTestSuite(I18NFormatterTestGwt.class);
        suite.addTestSuite(DockLayoutPageViewTestGwt.class);
        suite.addTestSuite(PageManagerImplTestGwt.class);
        suite.addTestSuite(PagesTestGwt.class);
        suite.addTestSuite(AlbumDetailsPageTestGwt.class);
        suite.addTestSuite(ArtistDetailsPageTestGwt.class);
        suite.addTestSuite(SongDetailsPageTestGwt.class);
        suite.addTestSuite(SynonymEditorTestGwt.class);
        suite.addTestSuite(ArtistOverviewPageTestGwt.class);
        suite.addTestSuite(OpenPageSingleElementHandlerTestGwt.class);
        suite.addTestSuite(OverviewPageTestGwt.class);
        suite.addTestSuite(RemoveControllerDlgTestGwt.class);
        suite.addTestSuite(AlbumOverviewPageTestGwt.class);
        suite.addTestSuite(SongOverviewPageTestGwt.class);
        suite.addTestSuite(ArtistDetailsTableTestGwt.class);
        suite.addTestSuite(AlbumDetailsPageTestGwt.class);
        suite.addTestSuite(SongDetailsTableTestGwt.class);

        return suite;
    }
}
