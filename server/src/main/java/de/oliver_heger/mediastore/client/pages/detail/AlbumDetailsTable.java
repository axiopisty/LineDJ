package de.oliver_heger.mediastore.client.pages.detail;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.view.client.ProvidesKey;

import de.oliver_heger.mediastore.client.LinkColumn;
import de.oliver_heger.mediastore.client.pageman.PageManager;
import de.oliver_heger.mediastore.client.pages.Pages;
import de.oliver_heger.mediastore.shared.ObjectUtils;
import de.oliver_heger.mediastore.shared.model.AlbumComparators;
import de.oliver_heger.mediastore.shared.model.AlbumInfo;

/**
 * <p>
 * A specialized details table implementation for displaying a list with data
 * objects with album information.
 * </p>
 *
 * @author Oliver Heger
 * @version $Id: $
 */
public class AlbumDetailsTable extends AbstractDetailsTable<AlbumInfo>
{
    /** The key provider. */
    private static final ProvidesKey<AlbumInfo> KEY_PROVIDER =
            new ProvidesKey<AlbumInfo>()
            {
                @Override
                public Object getKey(AlbumInfo item)
                {
                    return extractAlbumKey(item);
                }
            };

    /**
     * Creates a new instance of {@code AlbumDetailsTable} with default
     * settings.
     */
    public AlbumDetailsTable()
    {
        super(KEY_PROVIDER, new AlbumTableInitializer(), null);
    }

    /**
     * Extracts the key of an album data object. This implementation returns the
     * album's ID.
     *
     * @param item the album data object
     * @return the key for this data object
     */
    private static Object extractAlbumKey(AlbumInfo item)
    {
        return item.getAlbumID();
    }

    /**
     * An initializer implementation for the album table.
     */
    private static class AlbumTableInitializer implements
            TableInitializer<AlbumInfo>
    {
        @Override
        public void initializeTable(CellTable<AlbumInfo> table,
                ColumnSortEvent.ListHandler<AlbumInfo> sortHandler,
                PageManager pageManager)
        {
            Column<AlbumInfo, String> colName =
                    new LinkColumn<AlbumInfo>(pageManager, Pages.ALBUMDETAILS)
                    {
                        @Override
                        public Object getID(AlbumInfo obj)
                        {
                            return extractAlbumKey(obj);
                        }

                        @Override
                        public String getValue(AlbumInfo object)
                        {
                            return object.getName();
                        }
                    };
            table.addColumn(colName, "Name");
            table.setColumnWidth(colName, 50, Unit.PCT);
            colName.setSortable(true);
            sortHandler
                    .setComparator(colName, AlbumComparators.NAME_COMPARATOR);

            Column<AlbumInfo, String> colDuration =
                    new Column<AlbumInfo, String>(new TextCell())
                    {
                        @Override
                        public String getValue(AlbumInfo object)
                        {
                            return object.getFormattedDuration();
                        }
                    };
            table.addColumn(colDuration, "Duration");
            table.setColumnWidth(colDuration, 25, Unit.PCT);
            colDuration.setSortable(true);
            sortHandler.setComparator(colDuration,
                    AlbumComparators.DURATION_COMPARATOR);

            Column<AlbumInfo, String> colCount =
                    new Column<AlbumInfo, String>(new TextCell())
                    {
                        @Override
                        public String getValue(AlbumInfo object)
                        {
                            return String.valueOf(object.getNumberOfSongs());
                        }
                    };
            table.addColumn(colCount, "Songs");
            table.setColumnWidth(colCount, 10, Unit.PCT);
            colCount.setSortable(true);
            sortHandler.setComparator(colCount,
                    AlbumComparators.SONGCOUNT_COMPARATOR);

            Column<AlbumInfo, String> colYear =
                    new Column<AlbumInfo, String>(new TextCell())
                    {
                        @Override
                        public String getValue(AlbumInfo object)
                        {
                            return ObjectUtils.toString(object
                                    .getInceptionYear());
                        }
                    };
            table.addColumn(colYear, "Year");
            table.setColumnWidth(colYear, 15, Unit.PCT);
            colYear.setSortable(true);
            sortHandler
                    .setComparator(colYear, AlbumComparators.YEAR_COMPARATOR);
        }
    }
}
