package de.taz.app.android.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import de.taz.app.android.R
import de.taz.app.android.api.dto.SearchFilter
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.api.dto.Sorting

private const val KEY_POSITION = "KEY_POSITION"
const val DEFAULT_SEARCH_RESULTS_TO_FETCH = 20
const val RELOAD_BEFORE_LAST = 5

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
    var total = 0

    fun checkIfLoadMore(lastVisible: Int): Boolean {
        val rangeInWhereToLoadMore = searchResultsLiveData.value?.size?.minus(RELOAD_BEFORE_LAST) ?: total
        return rangeInWhereToLoadMore in 1..lastVisible
                && currentlyLoadingMore.value == false
                && rangeInWhereToLoadMore < total
    }
}