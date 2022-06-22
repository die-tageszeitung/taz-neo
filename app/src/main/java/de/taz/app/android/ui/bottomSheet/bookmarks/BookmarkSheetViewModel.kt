package de.taz.app.android.ui.bottomSheet.bookmarks

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.content.ContentService
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.ui.search.SearchResultPagerFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class BookmarkSheetViewModel(application: Application) : AndroidViewModel(application) {

    private val articleRepository: ArticleRepository = ArticleRepository.getInstance(application)
    private val articleFileNameLiveData: MutableLiveData<String?> = MutableLiveData(null)
    private val contentService: ContentService =
        ContentService.getInstance(application.applicationContext)
    private val apiService: ApiService =
        ApiService.getInstance(application.applicationContext)

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

    val articleStub: ArticleStub?
        get() = articleLiveData.value
    val isBookmarkedLiveData: LiveData<Boolean> =
        articleLiveData.map { article -> article?.bookmarked ?: false }

    /**
     * This function is for bookmarking articles "outside" an issue. Eg in the search result list.
     * It first updates the UI if a  [pagerFragment] is given,
     * then downloads the corresponding metadata
     * downloads the article and
     * finally bookmarks the article.
     */
    suspend fun downloadArticleAndSetBookmark(articleFileName: String, datePublished: Date, pagerFragment: SearchResultPagerFragment?) {
        pagerFragment?.setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
        withContext(Dispatchers.IO) {
            val issueForMetadata = apiService.getIssueByFeedAndDate(DISPLAYED_FEED, datePublished)
            contentService.downloadMetadata(issueForMetadata, maxRetries = 5)
            articleRepository.get(articleFileName)?.let {
                contentService.downloadToCache(it)
                articleRepository.bookmarkArticle(it)
            }
        }
    }
}