package de.taz.app.android.ui.search

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.R
import de.taz.app.android.api.dto.SearchFilter
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.api.dto.Sorting
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers

private const val KEY_POSITION = "KEY_POSITION"
private const val RELOAD_BEFORE_LAST = 5

class SearchResultPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

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
    val positionLiveData: MutableLiveData<Int?> =
        savedStateHandle.getLiveData(KEY_POSITION)
    val searchResultsLiveData = MutableLiveData<List<SearchHitDto>>(emptyList())
    val currentlyLoadingMore: MutableLiveData<Boolean> = MutableLiveData(false)
    private val articleRepository: ArticleRepository = ArticleRepository.getInstance(application)
    private val articleFileNameLiveData: MutableLiveData<String?> = MutableLiveData(null)
    var totalFound = 0


    var articleFileName
        get() = articleFileNameLiveData.value
        set(value) { articleFileNameLiveData.value = value }

    private val articleLiveData: LiveData<ArticleStub> = articleFileNameLiveData.switchMap {
        liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
            it?.let {
                emitSource(articleRepository.getStubLiveData(it))
            }
        }
    }

    val isBookmarkedLiveData: LiveData<Boolean> =
        articleLiveData.map { article -> article?.bookmarked ?: false }

    fun checkIfLoadMore(lastVisible: Int): Boolean {
        val rangeInWhereToLoadMore = searchResultsLiveData.value?.size?.minus(RELOAD_BEFORE_LAST) ?: totalFound
        val searchResultListSize = searchResultsLiveData.value?.size ?: 0
        return rangeInWhereToLoadMore in 1..lastVisible
                && currentlyLoadingMore.value == false
                && searchResultListSize < totalFound
    }
}