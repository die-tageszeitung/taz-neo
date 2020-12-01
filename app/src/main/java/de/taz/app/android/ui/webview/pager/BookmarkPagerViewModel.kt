package de.taz.app.android.ui.webview.pager

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val KEY_ARTICLE_FILE_NAME = "KEY_ARTICLE_FILE_NAME"

class BookmarkPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val issueRepository = IssueRepository.getInstance(application)
    val articleRepository = ArticleRepository.getInstance(application)

    val articleFileNameLiveData: MutableLiveData<String?> = savedStateHandle.getLiveData(KEY_ARTICLE_FILE_NAME)

    val currentIssueAndArticleLiveData: LiveData<Pair<IssueStub, String>> = MediatorLiveData<Pair<IssueStub, String>>().apply {
        addSource(articleFileNameLiveData) { articleFileName ->
            viewModelScope.launch(Dispatchers.IO) {
                articleFileName?.let { articleFileName ->
                    issueRepository.getIssueStubForArticle(articleFileName)?.let { issueStub ->
                        postValue( issueStub to articleFileName)
                    }
                }
            }
        }
    }

    val articleListLiveData = articleRepository.getBookmarkedArticleStubsLiveData()

    val currentIssue: IssueStub?
        get() = currentIssueAndArticleLiveData.value?.first
}