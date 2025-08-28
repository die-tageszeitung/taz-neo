package de.taz.app.android.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.api.variables.SearchFilter
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

private const val DEFAULT_SEARCH_RESULTS_TO_FETCH = 20
private const val RELOAD_BEFORE_LAST = 5
private const val MIN_PUB_DATE = "1986-09-01" // first taz publication online available


class SearchResultViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val log by Log

    private val apiService: ApiService = ApiService.getInstance(application.applicationContext)
    private val bookmarkRepository = BookmarkRepository.getInstance(application.applicationContext)

    val minPublicationDate: Date = requireNotNull(DateHelper.stringToDate(MIN_PUB_DATE))
    private val defaultAdvancedSearchOptions = if (BuildConfig.IS_LMD) {
        AdvancedSearchOptions.Default.copy(searchFilter = SearchFilter.LMd)
    } else {
        AdvancedSearchOptions.Default
    }

    private var startSearchJob: Job? = null
    private var loadMoreJob: Job? = null

    // Current search state
    private val _searchResults = MutableStateFlow<SearchResults?>(null)
    val searchResults: StateFlow<SearchResults?> = _searchResults.asStateFlow()

    // The search options that had been applied to start the current search
    private val appliedSearchOptions = MutableStateFlow<SearchOptions?>(null)

    // Ui state for the search result list
    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Init)

    // Error state for toasts/popups
    // might be merged to _searchUiState if needed for better handling
    private val _connectionError = MutableStateFlow<ConnectivityException?>(null)
    val connectionError = _connectionError.asStateFlow()
    fun connectionErrorWasHandled() {
        _connectionError.value = null
    }

    fun getSearchHit(position: Int): SearchHit? {
        return _searchResults.value?.results?.getOrNull(position)
    }

    fun clearSearch() {
        _searchUiState.value = SearchUiState.Init
        _searchResults.value = null
        appliedSearchOptions.value = null
        _selectedSearchOptions.value = SearchOptions("", defaultAdvancedSearchOptions)
        _isAdvancedSearchOpen.value = false
    }

    fun startSearch(searchOptions: SearchOptions) {
        if (!searchOptions.isValid()) {
            log.warn("Search must be started with valid search options")
            return
        }

        val prevStartSearchJob = startSearchJob
        startSearchJob = viewModelScope.launch {
            // Cancel and await until the previous search jobs have stopped
            prevStartSearchJob?.cancelAndJoin()
            loadMoreJob?.cancelAndJoin()

            _searchResults.value = null
            _searchUiState.value = SearchUiState.Loading
            appliedSearchOptions.value = searchOptions

            try {
                val advancedOptions = searchOptions.advancedOptions
                val (from, until) = getDateRange(advancedOptions.publicationDateFilter)
                val search = apiService.search(
                    searchOptions.searchText,
                    advancedOptions.title,
                    advancedOptions.author,
                    null,
                    DEFAULT_SEARCH_RESULTS_TO_FETCH,
                    0,
                    from,
                    until,
                    advancedOptions.searchFilter,
                    advancedOptions.sorting,
                )

                if (search == null || search.total == 0 || search.sessionId == null) {
                    _searchUiState.value = SearchUiState.NoResults
                } else {
                    val newSearchResults = SearchResults(
                        search.sessionId,
                        searchOptions,
                        search.total,
                        search.searchHitList ?: emptyList()
                    )
                    _searchUiState.value = SearchUiState.Results(
                        newSearchResults, isLoadingMore = false
                    )
                    _searchResults.value = newSearchResults
                }
            } catch (e: ConnectivityException) {
                _searchUiState.value = SearchUiState.Init
                _connectionError.value = e
            }
        }
    }

    fun restartSearch() {
        val searchOptions = appliedSearchOptions.value
        if (searchOptions != null) {
            startSearch(searchOptions)
        }
    }

    fun tryLoadMore(lastVisibleResultIndex: Int) {
        val searchJobIsActive = startSearchJob?.isActive == true || loadMoreJob?.isActive == true
        if (!searchJobIsActive) {
            loadMoreJob = viewModelScope.launch {
                val currentSearchResults = searchResults.value

                if (currentSearchResults == null) {
                    log.warn("Can't load more - no previous search results stored.")
                    return@launch
                }

                val loadedResults = currentSearchResults.loadedResults
                val totalResults = currentSearchResults.totalResults
                if (loadedResults > 0
                    && lastVisibleResultIndex + RELOAD_BEFORE_LAST >= loadedResults
                    && lastVisibleResultIndex + 1 < totalResults
                ) {
                    loadMore(currentSearchResults)
                }
            }
        }
    }

    private suspend fun loadMore(currentSearchResults: SearchResults) {
        if (currentSearchResults.loadedResults >= currentSearchResults.totalResults) {
            log.verbose("Can't load more - reached the end of the search results")
            return
        }
        val offset = currentSearchResults.loadedResults

        _searchUiState.value = SearchUiState.Results(currentSearchResults, isLoadingMore = true)

        try {
            val searchOptions = currentSearchResults.searchOptions
            val advancedOptions = searchOptions.advancedOptions
            val (from, until) = getDateRange(advancedOptions.publicationDateFilter)
            val search = apiService.search(
                searchOptions.searchText,
                advancedOptions.title,
                advancedOptions.author,
                currentSearchResults.sessionId,
                DEFAULT_SEARCH_RESULTS_TO_FETCH,
                offset,
                from,
                until,
                advancedOptions.searchFilter,
                advancedOptions.sorting,
            )

            if (search == null || search.total == 0 || search.sessionId == null) {
                // If we did not get any new data, just keep the old results
                _searchUiState.value =
                    SearchUiState.Results(currentSearchResults, isLoadingMore = false)

            } else {
                if (search.sessionId != currentSearchResults.sessionId) {
                    log.warn("The session id changed while loading more data")
                }

                val newResults = search.searchHitList ?: emptyList()
                val extendedResults = currentSearchResults.results + newResults

                val newSearchResults = SearchResults(
                    search.sessionId, searchOptions, search.total, extendedResults
                )

                _searchUiState.value =
                    SearchUiState.Results(newSearchResults, isLoadingMore = false)
                _searchResults.value = newSearchResults
            }

        } catch (e: ConnectivityException) {
            _searchUiState.value = SearchUiState.Results(currentSearchResults, isLoadingMore = false)
            _connectionError.value = e
        }
    }

    private fun getDateRange(publicationDateFilter: PublicationDateFilter): Pair<String?, String?> {
        val today = Calendar.getInstance().time
        val (from, until) = when (publicationDateFilter) {
            PublicationDateFilter.Any -> null to null
            is PublicationDateFilter.Custom -> publicationDateFilter.from to publicationDateFilter.until
            PublicationDateFilter.Last31Days -> DateHelper.lastMonth() to today
            PublicationDateFilter.Last365Days -> DateHelper.lastYear() to today
            PublicationDateFilter.Last7Days -> DateHelper.lastWeek() to today
            PublicationDateFilter.LastDay -> DateHelper.yesterday() to today
        }

        return from?.let { simpleDateFormat.format(it) } to until?.let { simpleDateFormat.format(it) }
    }


    // region advanced search ui state
    // The current input/ui state of the advanced search options.
    private val _selectedSearchOptions =
        MutableStateFlow(SearchOptions("", defaultAdvancedSearchOptions))
    val selectedSearchOptions: StateFlow<SearchOptions> = _selectedSearchOptions.asStateFlow()

    fun setSelectedSearchText(searchText: String) {
        _selectedSearchOptions.value = _selectedSearchOptions.value.copy(searchText = searchText)
    }

    fun setSelectedAdvancedOptions(advancedOptions: AdvancedSearchOptions) {
        _selectedSearchOptions.value =
            _selectedSearchOptions.value.copy(advancedOptions = advancedOptions)
    }

    private val _isAdvancedSearchOpen: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isAdvancedSearchOpen: StateFlow<Boolean> = _isAdvancedSearchOpen.asStateFlow()

    // Ui state for the search result list with the additional open state of the advanced search overlay
    val searchUiStateWithAdvancedSearchOpen: Flow<Pair<SearchUiState, Boolean>> =
        _searchUiState.combine(_isAdvancedSearchOpen) { searchUiState, isAdvancedSearchOpen ->
            searchUiState to isAdvancedSearchOpen
        }

    fun toggleAdvancedSearchOpen() {
        // Reset the UI state to the currently shown search results (if there are any)
        _searchResults.value?.searchOptions?.advancedOptions?.let { currentAdvancedOptions ->
            _selectedSearchOptions.value = _selectedSearchOptions.value.copy(
                advancedOptions = currentAdvancedOptions
            )
        }
        _isAdvancedSearchOpen.value = !_isAdvancedSearchOpen.value
    }

    fun closeAdvancedSearch() {
        _isAdvancedSearchOpen.value = false
    }

    // The advanced search should be highlighted, if
    // 1. the overlay is open
    // 2. the currently shown results contain some advanced options
    // 3. there are currently no results shown, but the advanced search ui state contains some changes
    val isAdvancedSearchHighlighted: Flow<Boolean> =
        combine(_searchResults, _selectedSearchOptions, _isAdvancedSearchOpen) { searchResults, selectedSearchOptions, isAdvancedSearchOpen ->
            isAdvancedSearchOpen
                    || (searchResults != null && searchResults.searchOptions.advancedOptions != defaultAdvancedSearchOptions)
                    || (searchResults == null && selectedSearchOptions.advancedOptions != defaultAdvancedSearchOptions)
        }
    // endregion


    // region helper function for the [SearchResultPagerFragment]
    // Set the currently viewed position to update the bookmarkstate etc
    private val currentPosition = MutableStateFlow(0)
    fun setCurrentPosition(position: Int) {
        currentPosition.value = position
    }

    private val currentArticleFileName = currentPosition.map {
        getSearchHit(it)?.articleFileName
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val isBookmarkedFlow =
        currentArticleFileName.flatMapLatest { articleFileName ->
            if (articleFileName != null) {
                bookmarkRepository.createBookmarkStateFlow(articleFileName)
            } else {
                emptyFlow<Boolean>()
            }
        }

    val isBookmarkedLiveData: LiveData<Boolean> = isBookmarkedFlow.asLiveData()
    // endregion
}