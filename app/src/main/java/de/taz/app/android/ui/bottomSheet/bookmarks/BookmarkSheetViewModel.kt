package de.taz.app.android.ui.bottomSheet.bookmarks

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.content.ContentService
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
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

    suspend fun downloadArticleAndASetBookmark(articleFileName: String, datePublished: Date) {
        val issueForMetadata = apiService.getIssueByFeedAndDate(DISPLAYED_FEED, datePublished)
        contentService.downloadMetadata(issueForMetadata)
        articleRepository.get(articleFileName)?.let {
            contentService.downloadToCache(it)
            articleRepository.bookmarkArticle(it)
        }
    }
}