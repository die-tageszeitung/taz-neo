package de.taz.app.android.ui.webview.pager

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlin.time.Duration

private const val KEY_ARTICLE_FILE_NAME = "KEY_ARTICLE_FILE_NAME"

class BookmarkPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val issueRepository = IssueRepository.getInstance(application.applicationContext)
    private val articleRepository = ArticleRepository.getInstance(application.applicationContext)
    private val bookmarkRepository = BookmarkRepository.getInstance(application.applicationContext)

    val articleFileNameLiveData: MutableLiveData<String?> = savedStateHandle.getLiveData(KEY_ARTICLE_FILE_NAME)

    val currentIssueAndArticleLiveData: LiveData<Pair<IssueStub, String>> = MediatorLiveData<Pair<IssueStub, String>>().apply {
        addSource(articleFileNameLiveData) { articleFileName ->
            viewModelScope.launch {
                articleFileName?.let { articleFileName ->
                    issueRepository.getIssueStubForArticle(articleFileName)?.let { issueStub ->
                        postValue(issueStub to articleFileName)
                    }
                }
            }
        }
    }

    /**
     * IS_LMD
     */
    // Share the bookmarked articles flow, so that there is only one sql query necessary on updates
    // SharingStarted.WhileSubscribed(replayExpiration = Duration.ZERO) ensures that no query is called while there are no subscribers
    // Be reminded, that every map on a db result flow will call the query
    private val bookmarkedArticlesFlow = bookmarkRepository.getBookmarkedArticlesFlow().shareIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(replayExpiration = Duration.ZERO),
        1
    )
    val bookmarkedArticleStubsLiveData = bookmarkedArticlesFlow
        .map { articleList -> articleList.map { ArticleStub(it) } }
        .asLiveData()
    val bookmarkedArticlesLiveData = bookmarkedArticlesFlow.asLiveData()

    val currentIssue: IssueStub?
        get() = currentIssueAndArticleLiveData.value?.first

    fun toggleBookmark(articleStub: ArticleStub) {
        bookmarkRepository.toggleBookmarkAsync(articleStub.articleFileName)
    }

    fun bookmarkArticle(article: Article) {
        bookmarkRepository.addBookmarkAsync(article.key)
    }

    fun debookmarkArticle(article: Article) {
        bookmarkRepository.removeBookmarkAsync(article.key)
    }
}