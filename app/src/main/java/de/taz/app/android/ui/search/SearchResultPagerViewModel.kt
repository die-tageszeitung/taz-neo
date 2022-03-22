package de.taz.app.android.ui.search

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.dto.SearchHitDto

private const val KEY_ARTICLE_FILE_NAME = "KEY_ARTICLE_FILE_NAME"

class SearchResultPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val articleFileNameLiveData: MutableLiveData<String?> = savedStateHandle.getLiveData(KEY_ARTICLE_FILE_NAME)

    val searchResultsLiveData = MutableLiveData<List<SearchHitDto>>(emptyList())

}