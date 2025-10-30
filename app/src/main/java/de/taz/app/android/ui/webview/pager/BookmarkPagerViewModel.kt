package de.taz.app.android.ui.webview.pager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlin.time.Duration

private const val KEY_ARTICLE_FILE_NAME = "KEY_ARTICLE_FILE_NAME"

class BookmarkPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val bookmarkRepository = BookmarkRepository.getInstance(application.applicationContext)
    private val issueRepository = IssueRepository.getInstance(application.applicationContext)

    val articleFileNameFlow: MutableStateFlow<String?> = savedStateHandle.getMutableStateFlow(KEY_ARTICLE_FILE_NAME, null)

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
    val bookmarkedArticleStubsFlow = bookmarkedArticlesFlow
        .map { articleList -> articleList.map { ArticleStub(it) } }

    val bookmarkedArticlesLiveData = bookmarkedArticlesFlow.asLiveData()

    fun toggleBookmark(article: ArticleOperations) {
        bookmarkRepository.toggleBookmarkAsync(article)
    }

    fun bookmarkArticle(article: ArticleOperations) {
        bookmarkRepository.addBookmarkAsync(article)
    }

    fun debookmarkArticle(article: ArticleOperations) {
        bookmarkRepository.removeBookmarkAsync(article)
    }

    suspend fun removeAllBookmarks() {
        bookmarkRepository.removeAllBookmarks()
    }

}