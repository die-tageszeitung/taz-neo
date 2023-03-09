package de.taz.app.android.ui.search

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.R
import de.taz.app.android.api.variables.SearchFilter
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.api.models.Sorting
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

private const val RELOAD_BEFORE_LAST = 5
private const val MIN_PUB_DATE = "1986-09-01" // first taz publication online available

class SearchResultPagerViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val bookmarkRepository = BookmarkRepository.getInstance(application.applicationContext)

    val chosenTimeSlot: MutableLiveData<String> =
        MutableLiveData(application.getString(R.string.search_advanced_radio_timeslot_any))
    val chosenPublishedIn: MutableLiveData<String> =
        MutableLiveData(application.getString(R.string.search_advanced_radio_published_in_any))
    val chosenSortBy: MutableLiveData<String> =
        MutableLiveData(application.getString(R.string.search_advanced_radio_sort_by_relevance))
    val searchFilter: MutableLiveData<SearchFilter> = MutableLiveData(SearchFilter.all)
    val sorting: MutableLiveData<Sorting> = MutableLiveData(Sorting.relevance)
    val pubDateFrom: MutableLiveData<String> = MutableLiveData(null)
    val pubDateUntil: MutableLiveData<String> = MutableLiveData(null)
    val searchText: MutableLiveData<String> = MutableLiveData(null)
    val searchTitle: MutableLiveData<String> = MutableLiveData(null)
    val searchAuthor: MutableLiveData<String> = MutableLiveData(null)
    val searchResultsLiveData = MutableLiveData<List<SearchHit>>(emptyList())
    val currentlyLoadingMore: MutableLiveData<Boolean> = MutableLiveData(false)
    private val articleRepository: ArticleRepository = ArticleRepository.getInstance(application)
    private val articleFileNameLiveData: MutableLiveData<String?> = MutableLiveData(null)
    var sessionId: String? = null
    var totalFound = 0
    var minPubDate = MIN_PUB_DATE

    var articleFileName
        get() = articleFileNameLiveData.value
        set(value) { articleFileNameLiveData.value = value }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val isBookmarkedFlow = articleFileNameLiveData.asFlow().flatMapLatest { articleFileName ->
        if (articleFileName != null) {
            bookmarkRepository.createBookmarkStateFlow(articleFileName)
        } else {
            emptyFlow<Boolean>()
        }
    }

    val isBookmarkedLiveData: LiveData<Boolean> = isBookmarkedFlow.asLiveData()

    fun checkIfLoadMore(lastVisible: Int): Boolean {
        val rangeInWhereToLoadMore =
            searchResultsLiveData.value?.size?.minus(RELOAD_BEFORE_LAST) ?: totalFound
        val searchResultListSize = searchResultsLiveData.value?.size ?: 0
        return rangeInWhereToLoadMore in 1..lastVisible
                && currentlyLoadingMore.value == false
                && searchResultListSize < totalFound
    }
}