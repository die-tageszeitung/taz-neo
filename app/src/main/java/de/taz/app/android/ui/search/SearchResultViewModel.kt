package de.taz.app.android.ui.search

import androidx.lifecycle.ViewModel
import de.taz.app.android.api.models.SearchHit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Temporary addition to [SearchResultPagerViewModel] until both of them might be merged.
 * It is only used from the [SearchResultPagerItemFragment] and the [SearchResultPagerFragment] for
 * paging through the search result articles.
 */
class SearchResultViewModel : ViewModel() {
    var totalResults: Int = 0
    var sessionId: String? = null

    var searchResults: List<SearchHit> = emptyList()
        private set(value) {
            _loadedSearchResults.value = value.size
            field = value
        }

    private val _loadedSearchResults: MutableStateFlow<Int> = MutableStateFlow(0)
    val loadedSearchResults = _loadedSearchResults.asStateFlow()

    fun setSearchResults(sessionId: String, searchResults: List<SearchHit>, totalResults: Int) {
        this.sessionId = sessionId
        this.searchResults = searchResults
        this.totalResults = totalResults
    }

    fun clearSearchResults() {
        sessionId = null
        searchResults = emptyList()
        totalResults = 0
    }

    fun addSearchResults(newSearchResults: List<SearchHit>) {
        searchResults = searchResults + newSearchResults
    }

    /**
     * Temporary helper to map state from [SearchResultPagerViewModel].
     * With a future refactoring both ViewModels should be merged.
     */
    fun mapFromSearchResultPagerViewModel(sessionId: String?, updatedSearchResults: List<SearchHit>, totalResults: Int) {
        if (sessionId == null) {
            clearSearchResults()
        } else if (sessionId == this.sessionId && searchResults.size < updatedSearchResults.size) {
            val newSearchResults = updatedSearchResults.subList(
                searchResults.size,
                updatedSearchResults.lastIndex
            )
            addSearchResults(newSearchResults)
        } else {
            // Store a copy of the list on this viewmodel
            setSearchResults(
                sessionId,
                updatedSearchResults.toList(),
                totalResults
            )
        }
    }
}