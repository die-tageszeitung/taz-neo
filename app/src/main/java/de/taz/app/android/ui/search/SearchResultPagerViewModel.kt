package de.taz.app.android.ui.search

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val KEY_ARTICLE_FILE_NAME = "KEY_ARTICLE_FILE_NAME"

class SearchResultPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val articleRepository = ArticleRepository.getInstance(application)

    val articleFileNameLiveData: MutableLiveData<String?> = savedStateHandle.getLiveData(KEY_ARTICLE_FILE_NAME)

    val bookmarkedArticleStubsLiveData = articleRepository.getBookmarkedArticleStubsLiveData()
    val bookmarkedArticlesLiveData = articleRepository.getBookmarkedArticlesLiveData()

}