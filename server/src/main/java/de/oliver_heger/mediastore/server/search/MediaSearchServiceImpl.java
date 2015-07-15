package de.oliver_heger.mediastore.server.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import de.oliver_heger.mediastore.server.RemoteMediaServiceServlet;
import de.oliver_heger.mediastore.server.convert.AlbumEntityConverter;
import de.oliver_heger.mediastore.server.convert.ArtistEntityConverter;
import de.oliver_heger.mediastore.server.convert.ConvertUtils;
import de.oliver_heger.mediastore.server.convert.EntityConverter;
import de.oliver_heger.mediastore.server.convert.SongEntityConverter;
import de.oliver_heger.mediastore.server.db.JPATemplate;
import de.oliver_heger.mediastore.server.model.AlbumEntity;
import de.oliver_heger.mediastore.server.model.ArtistEntity;
import de.oliver_heger.mediastore.server.model.Finders;
import de.oliver_heger.mediastore.server.model.SongEntity;
import de.oliver_heger.mediastore.shared.model.AlbumInfo;
import de.oliver_heger.mediastore.shared.model.ArtistInfo;
import de.oliver_heger.mediastore.shared.model.SongInfo;
import de.oliver_heger.mediastore.shared.search.MediaSearchParameters;
import de.oliver_heger.mediastore.shared.search.MediaSearchService;
import de.oliver_heger.mediastore.shared.search.OrderDef;
import de.oliver_heger.mediastore.shared.search.SearchIterator;
import de.oliver_heger.mediastore.shared.search.SearchIteratorImpl;
import de.oliver_heger.mediastore.shared.search.SearchResult;
import de.oliver_heger.mediastore.shared.search.SearchResultImpl;

/**
 * <p>
 * The Google RPC-based implementation of the {@link MediaSearchService}
 * interface.
 * </p>
 * <p>
 * This implementation supports two different search modes:
 * <ul>
 * <li>If no search text is provided, results are retrieved directly from the
 * database with full paging support. The {@code firstResult} and
 * {@code maxResults} properties of the {@link MediaSearchParameters} object are
 * evaluated. A search iterator object is ignored if one is specified. The
 * iterator object associated with the search result always indicates that no
 * more results are available.</li>
 * <li>If a search text is provided, a chunk-based search is performed because a
 * full table scan has to be performed. In this case only the {@code maxResults}
 * property of the {@link MediaSearchParameters} object is taken into account.
 * The first position of the result set is determined by the passed in search
 * iterator. The client is responsible for invoking the search method repeatedly
 * until all desired results are retrieved or the table has been scanned
 * completely.</li>
 * </ul>
 * Both search modes are implemented by a single method for the corresponding
 * result type.
 * </p>
 *
 * @author Oliver Heger
 * @version $Id: $
 */
public class MediaSearchServiceImpl extends RemoteMediaServiceServlet implements
        MediaSearchService
{
    /**
     * The serial version UID.
     */
    private static final long serialVersionUID = 20100911L;

    /** Constant for the user ID parameter. */
    private static final String PARAM_USRID = "userID";

    /** Constant for the select clause which selects an entity. */
    private static final String SELECT_ENTITY = "select e";

    /** Constant for the select clause for determining the number of results. */
    private static final String SELECT_COUNT = "select count(e)";

    /** Constant for the query select prefix. */
    private static final String SELECT_PREFIX = SELECT_ENTITY + " from ";

    /** Constant for the WHERE part of the query with the user ID constraint. */
    private static final String WHERE_USER = " e where e.user = :"
            + PARAM_USRID;

    /** Constant for the entity prefix. */
    private static final String ENTITY_PREFIX = "e.";

    /** Constant for the ORDER BY clause. */
    private static final String ORDER_BY = " order by ";

    /** Constant for the query for artists. */
    private static final String QUERY_ARTISTS = SELECT_PREFIX + "ArtistEntity"
            + WHERE_USER;

    /** Constant for the query string for songs. */
    private static final String QUERY_SONGS = SELECT_PREFIX + "SongEntity"
            + WHERE_USER;

    /** Constant for the query string for albums. */
    private static final String QUERY_ALBUMS = SELECT_PREFIX + "AlbumEntity"
            + WHERE_USER;

    /** Constant for the property for the default order. */
    private static final String DEFAULT_ORDER_PROPERTY = "searchName";

    /** Constant for the separator used in the ORDER BY clause. */
    private static final Object ORDER_SEPARATOR = ", ";

    /** Constant for the default order definition. */
    private static final OrderDef DEFAULT_ORDER;

    /** Constant for the default chunk size for search operations. */
    private static final int DEFAULT_CHUNK_SIZE = 50;

    /** Constant for the default buffer size for string generation. */
    private static final int BUF_SIZE = 256;

    /** Stores the chunk size used for search operations. */
    private int chunkSize = DEFAULT_CHUNK_SIZE;

    /**
     * Returns the current chunk size for search operations.
     *
     * @return the chunk size for search operations
     */
    public int getChunkSize()
    {
        return chunkSize;
    }

    /**
     * Sets the chunk size for search operations. This is the number of records
     * processed in a single search step; then the results obtained so far are
     * sent back to the client. The client can then initiate the next search
     * step.
     *
     * @param chunkSize the chunk size
     */
    public void setChunkSize(int chunkSize)
    {
        this.chunkSize = chunkSize;
    }

    /**
     * Performs a search for artists. The search can be performed in multiple
     * iterations. If a search iterator is provided, a search continues.
     *
     * @param params search parameters
     * @param iterator the search iterator
     * @return the search results
     */
    @Override
    public SearchResult<ArtistInfo> searchArtists(MediaSearchParameters params,
            SearchIterator iterator)
    {
        if (params.getSearchText() == null)
        {
            SearchIteratorImpl sit = new SearchIteratorImpl();
            List<ArtistEntity> artists =
                    executeFullSearch(params, sit, QUERY_ARTISTS);
            return new SearchResultImpl<ArtistInfo>(
                    ConvertUtils.convertEntities(artists,
                            createArtistSearchConverter()), sit, params);
        }
        return executeChunkSearch(params, iterator,
                createArtistSearchFilter(params),
                createArtistSearchConverter(), QUERY_ARTISTS);
    }

    /**
     * Performs a search for songs. Based on the parameters object this method
     * either performs a full search or a chunk search.
     *
     * @param params search parameters
     * @param iterator the search iterator
     * @return the search results
     */
    @Override
    public SearchResult<SongInfo> searchSongs(MediaSearchParameters params,
            SearchIterator iterator)
    {
        if (params.getSearchText() == null)
        {
            SearchIteratorImpl sit = new SearchIteratorImpl();
            List<SongEntity> songs =
                    executeFullSearch(params, sit, QUERY_SONGS);
            SongEntityConverter conv =
                    createAndInitializeSongSearchConverter(songs);
            return new SearchResultImpl<SongInfo>(ConvertUtils.convertEntities(
                    songs, conv), sit, params);
        }

        return executeChunkSearch(params, iterator,
                createSongSearchFilter(params), createSongSearchConverter(),
                QUERY_SONGS);
    }

    /**
     * Performs a search for albums. Based on the parameters objects either a
     * full or a chunk search is performed. In both cases extended information
     * is obtained about the album based on the albums associated with it.
     *
     * @param params search parameters
     * @param iterator the search iterator
     * @return the search results
     */
    @Override
    public SearchResult<AlbumInfo> searchAlbums(MediaSearchParameters params,
            SearchIterator iterator)
    {
        if (params.getSearchText() == null)
        {
            SearchIteratorImpl sit = new SearchIteratorImpl();
            List<AlbumEntity> albums =
                    executeFullSearch(params, sit, QUERY_ALBUMS);
            AlbumEntityConverter conv =
                    createAndInitializeAlbumSearchConverter(albums);
            return new SearchResultImpl<AlbumInfo>(
                    ConvertUtils.convertEntities(albums, conv), sit, params);
        }

        return executeChunkSearch(params, iterator,
                createAlbumSearchFilter(params), createAlbumSearchConverter(),
                QUERY_ALBUMS);
    }

    /**
     * Creates some test data for the currently logged in user. This
     * implementation delegates to a helper class for creating the dummy data.
     */
    @Override
    public void createTestData()
    {
        new DummyDataCreator(getCurrentUser(true)).createTestData();
    }

    /**
     * Tests the passed in search iterator to determine whether a new search has
     * to be started. This is the case if the iterator is <b>null</b> or new
     * search key is defined. This method also checks whether the iterator is of
     * the expected type. If not, an exception is thrown.
     *
     * @param it the iterator
     * @return a flag whether a new search has to be started
     * @throws IllegalArgumentException if the iterator object is not of the
     *         expected type
     */
    boolean isNewSearch(SearchIterator it)
    {
        if (it == null)
        {
            return true;
        }

        if (!(it instanceof SearchIteratorImpl))
        {
            throw new IllegalArgumentException("Unsupported search iterator: "
                    + it);
        }

        return it.getCurrentPosition() == 0;
    }

    /**
     * Initializes the search iterator for the current search. This method
     * checks whether a new search is started or a search is continued. Then
     * either a new {@link SearchIteratorImpl} object is created or the existing
     * one is updated correspondingly.
     *
     * @param em the {@code EntityManager}
     * @param query the query string
     * @param it the iterator object passed to the server; this method expects
     *        that it is of type {@code SearchIteratorImpl}, otherwise an
     *        exception is thrown
     * @return the iterator object for the current search
     * @throws IllegalArgumentException if the iterator object is not of the
     *         expected type
     */
    SearchIteratorImpl initializeSearchIterator(EntityManager em, String query,
            SearchIterator it)
    {
        if (isNewSearch(it))
        {
            SearchIteratorImpl sit = newSearchIterator(em, query);
            return sit;
        }
        else
        {
            SearchIteratorImpl sit = (SearchIteratorImpl) it;
            return sit;
        }
    }

    /**
     * Creates a filter object for an artist search.
     *
     * @param params the search parameters
     * @return the filter for artists
     */
    ArtistSearchFilter createArtistSearchFilter(MediaSearchParameters params)
    {
        assert params.getSearchText() != null : "No search text!";
        return new ArtistSearchFilter(params.getSearchText());
    }

    /**
     * Returns a converter to be used for converting artist result objects.
     *
     * @return the converter for artists
     */
    ArtistEntityConverter createArtistSearchConverter()
    {
        return ArtistEntityConverter.INSTANCE;
    }

    /**
     * Creates a filter object for a song search.
     *
     * @param params the search parameters
     * @return the filter for songs
     */
    SearchFilter<SongEntity> createSongSearchFilter(MediaSearchParameters params)
    {
        return new SongSearchFilter(params.getSearchText());
    }

    /**
     * Creates a converter object for converting song result objects.
     *
     * @return the converter for songs
     */
    EntityConverter<SongEntity, SongInfo> createSongSearchConverter()
    {
        return new SongEntityConverter();
    }

    /**
     * Creates a filter object for an album search.
     *
     * @param params the search parameters
     * @return the filter for albums
     */
    SearchFilter<AlbumEntity> createAlbumSearchFilter(
            MediaSearchParameters params)
    {
        return new AlbumSearchFilter(params.getSearchText());
    }

    /**
     * Creates a converter object for converting album result objects.
     *
     * @return the converter for albums
     */
    EntityConverter<AlbumEntity, AlbumInfo> createAlbumSearchConverter()
    {
        return new AlbumEntityConverter();
    }

    /**
     * Executes a search query over all elements of a given type. This method
     * actually executes two queries: one for determining the total record count
     * and one for retrieving the result objects. The latter are returned as a
     * list. The passed in search iterator is initialized correspondingly.
     *
     * @param <D> the type of entity objects processed by this method
     * @param params the parameters object with search parameters
     * @param queryStr the query string to be executed
     * @param sit the search iterator to be initialized
     * @return the list with the results
     */
    <D> List<D> executeFullSearch(final MediaSearchParameters params,
            final SearchIteratorImpl sit, String queryStr)
    {
        final String orderedQuery = appendOrder(queryStr, params);
        JPATemplate<List<D>> template = new JPATemplate<List<D>>()
        {
            @Override
            protected List<D> performOperation(EntityManager em)
            {
                Number count =
                        (Number) prepareUserQuery(em,
                                createCountQuery(orderedQuery))
                                .getSingleResult();
                sit.setCurrentPosition(params.getFirstResult());
                sit.setRecordCount(count.longValue());
                sit.setHasNext(false);

                Query query = prepareUserQuery(em, orderedQuery);
                if (params.getFirstResult() > 0)
                {
                    query.setFirstResult(params.getFirstResult());
                }
                if (params.getMaxResults() > 0)
                {
                    query.setMaxResults(params.getMaxResults());
                    sit.initializePaging(Math.max(params.getFirstResult(), 0)
                            / params.getMaxResults(), (count.intValue()
                            + params.getMaxResults() - 1)
                            / params.getMaxResults());
                }
                else
                {
                    sit.initializePaging(0, 1);
                }

                // we assume that the query returns objects of the expected type
                @SuppressWarnings("unchecked")
                List<D> results = (List<D>) query.getResultList();
                return results;
            }
        };

        return template.execute();
    }

    /**
     * Executes an iteration of a chunk search. This method loads a chunk of
     * data, applies the filter to it, and performs necessary type conversion.
     *
     * @param <E> the type of entities to be loaded
     * @param <D> the data objects returned by this search
     * @param params the current search parameters
     * @param searchIterator the search iterator
     * @param filter the filter
     * @param converter the converter for converting to the resulting data
     *        objects
     * @param queryStr the query string
     * @return the results of the search
     */
    <E, D> SearchResult<D> executeChunkSearch(
            final MediaSearchParameters params,
            final SearchIterator searchIterator, final SearchFilter<E> filter,
            final EntityConverter<E, D> converter, String queryStr)
    {
        final String orderedQuery = appendOrder(queryStr, params);
        JPATemplate<SearchResult<D>> templ =
                new JPATemplate<SearchResult<D>>(false)
                {
                    @Override
                    protected SearchResult<D> performOperation(EntityManager em)
                    {
                        int resultSize = params.getMaxResults();
                        if (resultSize <= 0)
                        {
                            resultSize = Integer.MAX_VALUE;
                        }
                        int idx = 0;
                        List<D> resultList = new LinkedList<D>();

                        SearchIteratorImpl sit =
                                initializeSearchIterator(em, orderedQuery,
                                        searchIterator);
                        Query query =
                                prepareSearchQuery(em, orderedQuery,
                                        sit.getCurrentPosition());
                        // the query string should result objects of the correct
                        // type
                        @SuppressWarnings("unchecked")
                        List<E> resultSet =
                                query.setMaxResults(getChunkSize())
                                        .getResultList();
                        E lastEntity = null;
                        Iterator<E> it = resultSet.iterator();
                        JPATemplate.inject(em, converter);

                        while (it.hasNext() && resultList.size() < resultSize)
                        {
                            lastEntity = it.next();
                            if (filter.accepts(lastEntity))
                            {
                                resultList.add(converter.convert(lastEntity));
                            }
                            idx++;
                        }

                        sit.setHasNext(it.hasNext()
                                || resultSet.size() >= getChunkSize());
                        sit.setCurrentPosition(sit.getCurrentPosition() + idx);

                        return new SearchResultImpl<D>(resultList, sit, params);
                    }
                };

        return templ.execute();
    }

    /**
     * Retrieves all artist entities which are referenced by the given song
     * entities. This method is used to populate the artist name property in the
     * song info objects returned for song searches.
     *
     * @param songs the song entities to be converted
     * @return a list with referenced artist entities
     */
    List<ArtistEntity> fetchReferencedArtists(
            Collection<? extends SongEntity> songs)
    {
        Set<Long> artistIDs = new HashSet<Long>();
        for (SongEntity song : songs)
        {
            if (song.getArtistID() != null)
            {
                artistIDs.add(song.getArtistID());
            }
        }

        // the result type of the query is determined
        @SuppressWarnings("unchecked")
        List<ArtistEntity> result =
                (List<ArtistEntity>) fetchReferencedObjects(artistIDs,
                        ArtistEntity.QUERY_FIND_BY_IDS);
        return result;
    }

    /**
     * Retrieves all album entities which are referenced by the given song
     * entities. This method is used to populate the album name property in the
     * sing info objects returned for song searches.
     *
     * @param songs the song entities to be converted
     * @return a list with the referenced album entities
     */
    List<AlbumEntity> fetchReferencedAlbums(
            Collection<? extends SongEntity> songs)
    {
        Set<Long> albumIDs = new HashSet<Long>();
        for (SongEntity song : songs)
        {
            if (song.getAlbumID() != null)
            {
                albumIDs.add(song.getAlbumID());
            }
        }

        // the result type of the query is determined
        @SuppressWarnings("unchecked")
        List<AlbumEntity> result =
                (List<AlbumEntity>) fetchReferencedObjects(albumIDs,
                        AlbumEntity.QUERY_FIND_BY_IDS);
        return result;
    }

    /**
     * Retrieves all song entities which belong to one of the specified album
     * entities. This method is used to initialize the converter for albums
     * which also has to deal with information about the songs of the albums to
     * be converted.
     *
     * @param albums the album entities to be converted
     * @return a list with the songs associated with these albums
     */
    List<SongEntity> fetchSongsForAlbums(
            final Collection<? extends AlbumEntity> albums)
    {
        JPATemplate<List<SongEntity>> templ =
                new JPATemplate<List<SongEntity>>(false)
                {
                    @Override
                    protected List<SongEntity> performOperation(EntityManager em)
                    {
                        List<SongEntity> songs =
                                Finders.findSongsByAlbums(em, albums);
                        songs.size(); // ensure that list is loaded
                        return songs;
                    }
                };
        return templ.execute();
    }

    /**
     * Appends the ORDER BY clause to the given query string. We always use an
     * order definition. If no order is specified in the parameters object, per
     * default query results are sorted by the entity name.
     *
     * @param queryStr the query string
     * @param params the search parameters object
     * @return the query with the appended order definition
     */
    static String appendOrder(String queryStr, MediaSearchParameters params)
    {
        StringBuilder buf = new StringBuilder(BUF_SIZE);
        buf.append(queryStr).append(ORDER_BY);
        boolean first = true;

        for (OrderDef od : params.getOrderDefinitionDefault(DEFAULT_ORDER))
        {
            if (first)
            {
                first = false;
            }
            else
            {
                buf.append(ORDER_SEPARATOR);
            }
            buf.append(ENTITY_PREFIX);
            od.appendOrderDefinition(buf);
        }

        return buf.toString();
    }

    /**
     * Helper method for retrieving objects referenced by songs. This method
     * performs a query with an in condition for the specified IDs.
     *
     * @param ids the IDs to be retrieved
     * @param queryName the name of the query to be executed
     * @return the list with retrieved entities
     */
    private List<?> fetchReferencedObjects(final Collection<Long> ids,
            final String queryName)
    {
        JPATemplate<List<?>> templ = new JPATemplate<List<?>>(false)
        {
            @Override
            protected List<?> performOperation(EntityManager em)
            {
                Map<String, Object> params =
                        Collections.singletonMap(Finders.PARAM_ID,
                                (Object) new ArrayList<Long>(ids));
                List<?> result =
                        (List<?>) Finders.queryInCondition(em, queryName,
                                params, Finders.PARAM_ID);
                result.size(); // ensure that the entities are loaded
                return result;
            }
        };

        return templ.execute();
    }

    /**
     * Returns a converter for songs which is already initialized with entities
     * that are referenced by the song objects.
     *
     * @param songs the list with the songs to be converted
     * @return an initialized converter for song entities
     */
    private SongEntityConverter createAndInitializeSongSearchConverter(
            Collection<? extends SongEntity> songs)
    {
        SongEntityConverter conv =
                (SongEntityConverter) createSongSearchConverter();
        conv.initResolvedArtists(fetchReferencedArtists(songs));
        conv.initResolvedAlbums(fetchReferencedAlbums(songs));
        return conv;
    }

    /**
     * Returns a converter for albums which is already initialized with song
     * information needed for converting album entities.
     *
     * @param albums the list with the albums to be converted
     * @return the initialized converter for album entities
     */
    private AlbumEntityConverter createAndInitializeAlbumSearchConverter(
            Collection<? extends AlbumEntity> albums)
    {
        AlbumEntityConverter conv =
                (AlbumEntityConverter) createAlbumSearchConverter();
        conv.initializeSongData(fetchSongsForAlbums(albums));
        return conv;
    }

    /**
     * Helper method for creating a query which has the current user as
     * parameter. This method creates a query for the specified query string and
     * sets the parameter for the user ID.
     *
     * @param em the entity manager
     * @param queryStr the query string
     * @return the query object
     */
    private Query prepareUserQuery(EntityManager em, String queryStr)
    {
        return em.createQuery(queryStr).setParameter(PARAM_USRID,
                getCurrentUser(true));
    }

    /**
     * Prepares a search query. The query is initialized with the parameter for
     * the current user and the search parameter.
     *
     * @param em the entity manager
     * @param queryStr the query string
     * @param position the start position
     * @return the query object
     */
    private Query prepareSearchQuery(EntityManager em, String queryStr,
            long position)
    {
        Query query = prepareUserQuery(em, queryStr);
        query.setFirstResult((int) position);
        return query;
    }

    /**
     * Initializes a search iterator for a new search. The total record count is
     * already determined.
     *
     * @param em the entity manager
     * @param query the current query string
     * @return the new search iterator
     */
    private SearchIteratorImpl newSearchIterator(EntityManager em, String query)
    {
        SearchIteratorImpl sit = new SearchIteratorImpl();
        String countQuery = createCountQuery(query);
        Number count =
                (Number) prepareSearchQuery(em, countQuery, 0)
                        .getSingleResult();
        sit.setRecordCount(count.longValue());
        return sit;
    }

    /**
     * Generates a count query for the specified query string. This method
     * replaces the projection part of the query with a {@code count()}
     * expression.
     *
     * @param query the original query
     * @return the corresponding count query
     */
    private static String createCountQuery(String query)
    {
        String countQuery = query.replace(SELECT_ENTITY, SELECT_COUNT);
        int pos = countQuery.indexOf(ORDER_BY);
        if (pos > 0)
        {
            countQuery = countQuery.substring(0, pos);
        }
        return countQuery;
    }

    static
    {
        DEFAULT_ORDER = new OrderDef();
        DEFAULT_ORDER.setFieldName(DEFAULT_ORDER_PROPERTY);
    }
}
