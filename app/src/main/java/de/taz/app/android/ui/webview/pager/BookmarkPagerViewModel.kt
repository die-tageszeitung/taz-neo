package de.taz.app.android.ui.webview.pager

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private const val KEY_ARTICLE_FILE_NAME = "KEY_ARTICLE_FILE_NAME"

class BookmarkPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application), CoroutineScope {

    val issueRepository = IssueRepository.getInstance(application)
    val articleRepository = ArticleRepository.getInstance(application)

    val articleFileNameLiveData: MutableLiveData<String?> = savedStateHandle.getLiveData(KEY_ARTICLE_FILE_NAME)

    val currentIssueAndArticleLiveData: LiveData<Pair<IssueStub, String>> = MediatorLiveData<Pair<IssueStub, String>>().apply {
        addSource(articleFileNameLiveData) { articleFileName ->
            viewModelScope.launch(Dispatchers.IO) {
                articleFileName?.let { articleFileName ->
                    issueRepository.getIssueStubForArticle(articleFileName)?.let { issueStub ->
                        postValue(issueStub to articleFileName)
                    }
                }
            }
        }
    }

    val bookmarkedArticleStubsLiveData = articleRepository.getBookmarkedArticleStubsLiveData()
    val bookmarkedArticlesLiveData = articleRepository.getBookmarkedArticlesLiveData()

    val currentIssue: IssueStub?
        get() = currentIssueAndArticleLiveData.value?.first

    fun toggleBookmark(articleStub: ArticleStub) {
        launch {
            if (articleStub.bookmarkedTime != null) {
                articleRepository.debookmarkArticle(articleStub)
            } else {
                articleRepository.bookmarkArticle(articleStub)
            }
        }
    }

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.IO

}