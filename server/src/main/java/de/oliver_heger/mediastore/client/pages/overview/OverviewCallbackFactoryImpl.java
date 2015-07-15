package de.oliver_heger.mediastore.client.pages.overview;

import java.util.List;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.view.client.HasData;

import de.oliver_heger.mediastore.client.ErrorIndicator;
import de.oliver_heger.mediastore.shared.search.MediaSearchServiceAsync;
import de.oliver_heger.mediastore.shared.search.SearchIterator;
import de.oliver_heger.mediastore.shared.search.SearchResult;

/**
 * <p>
 * A default implementation of the {@code OverviewCallbackFactory} interface.
 * </p>
 * <p>
 * This implementation produces fully functional callback objects which are able
 * to pass the data retrieved from the media search service to a cell widget.
 * Error handling is supported, too, through an {@code ErrorIndicator} object
 * which can be passed to the constructor.
 * </p>
 *
 * @author Oliver Heger
 * @version $Id: $
 * @param <T> the type of data objects the callbacks have to deal with
 */
public class OverviewCallbackFactoryImpl<T> implements
        OverviewCallbackFactory<T>
{
    /** Stores the error indicator. */
    private final ErrorIndicator errorIndicator;

    /**
     * Creates a new instance of {@code OverviewCallbackFactoryImpl} and
     * initializes it with the {@code ErrorIndicator} object.
     *
     * @param errInd the {@code ErrorIndicator}
     */
    public OverviewCallbackFactoryImpl(ErrorIndicator errInd)
    {
        errorIndicator = errInd;
    }

    /**
     * Returns the {@code ErrorIndicator} used by this factory.
     *
     * @return the {@code ErrorIndicator}
     */
    public ErrorIndicator getErrorIndicator()
    {
        return errorIndicator;
    }

    /**
     * {@inheritDoc} This implementation returns a simple callback object which
     * directly passes the data received from the service call to the widget
     * object.
     */
    @Override
    public AsyncCallback<SearchResult<T>> createSimpleSearchCallback(
            HasData<T> widget)
    {
        return new SimpleSearchCallback(widget);
    }

    /**
     * {@inheritDoc} This implementation returns a callback which can perform a
     * search in multiple chunks if desired. Every time a chunk is received from
     * the server, the consumer gets notified.
     */
    @Override
    public AsyncCallback<SearchResult<T>> createParameterSearchCallback(
            MediaSearchServiceAsync searchService,
            OverviewQueryHandler<T> queryHandler, HasData<T> widget,
            SearchResultConsumer<T> consumer)
    {
        return new ParameterSearchCallback(widget, searchService, queryHandler,
                consumer);
    }

    /**
     * Notifies this object that a service call caused an error. The error is
     * passed to the {@code ErrorIndicator}.
     *
     * @param err the error received from the service call
     */
    private void errorCaught(Throwable err)
    {
        getErrorIndicator().displayError(err);
    }

    /**
     * Clears the error state of the {@code ErrorIndicator}.
     */
    private void clearError()
    {
        getErrorIndicator().clearError();
    }

    /**
     * The implementation of the simple callback. This callback directly passes
     * the data received from the service call to the cell widget.
     */
    private class SimpleSearchCallback implements
            AsyncCallback<SearchResult<T>>
    {
        /** The widget to be initialized. */
        private final HasData<T> widget;

        /**
         * Creates a new instance of {@code SimpleSearchCallback} and
         * initializes it with the widget to be updated.
         *
         * @param w the widget
         */
        public SimpleSearchCallback(HasData<T> w)
        {
            widget = w;
        }

        /**
         * An error occurred during the service call. This implementation passes
         * the error to the {@code ErrorIndicator}.
         *
         * @param caught the error
         */
        @Override
        public void onFailure(Throwable caught)
        {
            errorCaught(caught);
        }

        /**
         * The service call was successful. The widget is updated
         * correspondingly.
         *
         * @param result the result of the service call
         */
        @Override
        public void onSuccess(SearchResult<T> result)
        {
            widget.setRowCount((int) result.getSearchIterator()
                    .getRecordCount());
            widget.setRowData(result.getSearchParameters().getFirstResult(),
                    result.getResults());
            clearError();
        }
    }

    /**
     * The implementation of a parameter callback. A callback of this type is
     * more complex. It first has to check whether new data is available in the
     * current chunk. If so, it is pushed into the widget. If the search
     * iterator indicates that more chunks exist, search is continued.
     */
    private class ParameterSearchCallback implements
            AsyncCallback<SearchResult<T>>
    {
        /** The consumer of the search results. */
        private final SearchResultConsumer<T> consumer;

        /** The widget to be initialized. */
        private final HasData<T> widget;

        /** The search service. */
        private final MediaSearchServiceAsync searchService;

        /** The query handler. */
        private final OverviewQueryHandler<T> queryHandler;

        /**
         * Creates a new instance of {@code ParameterSearchCallback} and
         * initializes it.
         *
         * @param w the widget to be updated
         * @param svc the search service
         * @param handler the query handler
         * @param resConsumer the consumer of search results
         */
        public ParameterSearchCallback(HasData<T> w,
                MediaSearchServiceAsync svc, OverviewQueryHandler<T> handler,
                SearchResultConsumer<T> resConsumer)
        {
            widget = w;
            searchService = svc;
            queryHandler = handler;
            consumer = resConsumer;
        }

        /**
         * An error occurred during the service call. This implementation passes
         * the error to the {@code ErrorIndicator}.
         *
         * @param caught the error
         */
        @Override
        public void onFailure(Throwable caught)
        {
            errorCaught(caught);
        }

        /**
         * The service call was successful. This implementation writes the data
         * retrieved into the widget. If there is more data, another service
         * call is initiated.
         *
         * @param result the result object returned from the service
         */
        @Override
        public void onSuccess(SearchResult<T> result)
        {
            List<T> resultList = result.getResults();
            if (!resultList.isEmpty())
            {
                consumer.searchResultsReceived(resultList, widget, result
                        .getSearchParameters().getClientParameter());
            }
            clearError();
            checkForMoreResults(result);
        }

        /**
         * Checks if further results are available. If so, another service call
         * is executed.
         *
         * @param result the result object returned from the service
         */
        private void checkForMoreResults(SearchResult<T> result)
        {
            SearchIterator sit = result.getSearchIterator();
            if (sit.hasNext())
            {
                queryHandler.executeQuery(searchService,
                        result.getSearchParameters(), sit, this);
            }
        }
    }
}
