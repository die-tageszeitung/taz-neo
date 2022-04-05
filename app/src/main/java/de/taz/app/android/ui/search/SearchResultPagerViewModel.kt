package de.taz.app.android.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import de.taz.app.android.R
import de.taz.app.android.api.dto.SearchHitDto

private const val KEY_POSITION = "KEY_POSITION"

class SearchResultPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    var chosenTimeSlot: MutableLiveData<String> =
        MutableLiveData(application.getString(R.string.search_advanced_radio_timeslot_any))
    var chosenPublishedIn: MutableLiveData<String> =
        MutableLiveData(application.getString(R.string.search_advanced_radio_published_in_any))
    var chosenSortBy: MutableLiveData<String> =
        MutableLiveData(application.getString(R.string.search_advanced_radio_sort_by_relevance))
    var pubDateFrom: MutableLiveData<String> = MutableLiveData(null)
    var pubDateUntil: MutableLiveData<String> = MutableLiveData(null)
    val positionLiveData: MutableLiveData<Int?> =
        savedStateHandle.getLiveData(KEY_POSITION)
    val searchResultsLiveData = MutableLiveData<List<SearchHitDto>>(emptyList())

}