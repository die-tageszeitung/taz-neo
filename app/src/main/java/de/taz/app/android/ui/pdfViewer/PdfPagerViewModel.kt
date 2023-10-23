package de.taz.app.android.ui.pdfViewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.taz.app.android.METADATA_DOWNLOAD_RETRY_INDEFINITELY
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Frame
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.api.models.Page
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_NUMBER_OF_PAGES = 29
private const val KEY_CURRENT_ITEM = "KEY_CURRENT_ITEM"

data class PageWithArticles(
    val pagePdf: FileEntry,
    val pagina: String?,
    val articles: List<Article>? = null
)

sealed class OpenLinkEvent {
    class ShowArticle(val issueKey: IssueKey, val displayableKey: String?) : OpenLinkEvent()
    class ShowImprint(val issueKeyWithDisplayableKey: IssueKeyWithDisplayableKey) : OpenLinkEvent()
    class OpenExternal(val link: String) : OpenLinkEvent()
}

class PdfPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val log by Log

    private val authHelper = AuthHelper.getInstance(application.applicationContext)
    private val contentService: ContentService =
        ContentService.getInstance(application.applicationContext)
    private val fileEntryRepository =
        FileEntryRepository.getInstance(application.applicationContext)
    private val articleRepository = ArticleRepository.getInstance(application.applicationContext)
    private val issueRepository = IssueRepository.getInstance(application.applicationContext)
    private val imageRepository = ImageRepository.getInstance(application.applicationContext)

    private var issuePublication: IssuePublicationWithPages? = null

    private var issueFlow = MutableStateFlow<IssueWithPages?>(null)
    val issueLiveData = issueFlow.filterNotNull().asLiveData()

    val issue: IssueWithPages?
        get() = issueFlow.value

    private val pdfPageListFlow: SharedFlow<List<Page>?> = issueFlow
        .map(::pdfPageListMapper)
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)
    val pdfPageList: LiveData<List<Page>> = pdfPageListFlow.filterNotNull().asLiveData()

    val issueDownloadFailedErrorFlow = MutableStateFlow(false)

    private val _currentItem = savedStateHandle.getLiveData<Int>(KEY_CURRENT_ITEM)
    val currentItem = _currentItem as LiveData<Int>

    fun updateCurrentItem(position: Int) {
        viewModelScope.launch {
            updateCurrentItemInternal(position)
        }
    }

    private suspend fun updateCurrentItemInternal(position: Int) = withContext(Dispatchers.Main) {
        if (_currentItem.value == position) {
            return@withContext
        }

        val pdfPageList = pdfPageListFlow.first()
        val validPosition = position.coerceIn(0, pdfPageList?.size ?: DEFAULT_NUMBER_OF_PAGES)
        if (_currentItem.value != validPosition) {
            _currentItem.value = validPosition

            // Save current position to database to restore later on
            issueFlow.value?.issueKey?.let {
                issueRepository.saveLastPagePosition(
                    it.getIssueKey(),
                    validPosition
                )

            }
        }
    }

    fun setIssuePublication(issuePublication: IssuePublicationWithPages) {
        require(this.issuePublication == null || this.issuePublication == issuePublication) {
            "Each PdfPagerViewModel instance may only be used for exactly one issue. Updating it to another is not allowed."
        }

        if (this.issuePublication == null) {
            // Reload the issue if a new publication is set
            this.issuePublication = issuePublication
            loadIssue()
        }
    }

    private fun loadIssue() {
        val issuePublicationWithPages = this.issuePublication
        if (issuePublicationWithPages != null) {
            viewModelScope.launch {
                // We'll try to download the issues metadata 3 times.
                // If that fails (for example due to missing network) we will emit an error and retry
                // the download indefinitely.
                var issue = try {
                    issueDownloadFailedErrorFlow.emit(false)
                    downloadIssueMetadata(issuePublicationWithPages, maxRetries = 3)
                } catch (e: CacheOperationFailedException) {
                    // show dialog and retry infinitely
                    issueDownloadFailedErrorFlow.emit(true)
                    downloadIssueMetadata(issuePublicationWithPages)
                }

                // Then we start the actual download of the Issue data with the PDF pages.
                // While this will also download the issues metadata it does not return that data.
                // The download will be started on the application scope, so that it can finish even
                // if this ViewModel is destroyed - it will retry indefinitely.
                // We wait (join) until the operations coroutine has finished - note that we don't know if the
                // downloadToCache did succeed or failed: only that the launched coroutine has stopped.
                getApplicationScope().launch {
                    try {
                        contentService.downloadToCache(
                            download = IssuePublicationWithPages(issue.issueKey)
                        )
                    } catch (e: Exception) {
                        // Ignore all errors happening during the download in this coroutine.
                        // The actual check if the issue was downloaded fully, will be done below.
                        log.error("Failed to download full PDF issue", e)
                    }
                }.join()

                // Update the latest page position and the viewDate
                updateCurrentItemInternal(issue.lastPagePosition ?: 0)
                issueRepository.updateLastViewedDate(issue)

                // Last we'll get the latest issue entry from the database, so that we will have a correct download date
                val issueStub = issueRepository.getStub(issue.issueKey)
                if (issueStub == null) {
                    val hint =
                        "Issue ${issue.issueKey} that was just downloaded is not found in the database."
                    log.error(hint)
                    Sentry.captureMessage(hint)
                    issueDownloadFailedErrorFlow.emit(true)
                    return@launch
                }

                // We will not get the full issue from the database as this requires quite some work/time,
                // only the latest issue stub to update our metadata with
                issue = issue.copyWithMetadata(issueStub)

                if (issue.dateDownloadWithPages == null) {
                    val hint = "Issue ${issue.issueKey} was not fully downloaded."
                    log.error(hint)
                    Sentry.captureMessage(hint)
                    issueDownloadFailedErrorFlow.emit(true)
                    return@launch
                }

                // Finally store the downloaded issue and its data
                issueFlow.value = issue
            }
        } else {
            issueFlow.value = null
        }
    }

    private suspend fun pdfPageListMapper(issue: IssueWithPages?): List<Page>? {
        return if (issue == null || issue.dateDownloadWithPages == null) {
            null
        } else {
            issue.pageList.map {
                val fileEntry = requireNotNull(fileEntryRepository.get(it.pagePdf.name)) {
                    "Refreshing pagePdf fileEntry failed as fileEntry was null"
                }
                it.copy(pagePdf = fileEntry)
            }
        }
    }

    val navButton = MediatorLiveData<Image>().apply {
        val defaultDrawerFileName =
            getApplication<Application>().resources.getString(R.string.DEFAULT_NAV_DRAWER_FILE_NAME)
        viewModelScope.launch {
            postValue(imageRepository.get(defaultDrawerFileName))
        }
    } as LiveData<Image>

    fun goToPdfPage(link: String) {
        // it is only possible to go to another page if we are on a regular issue
        // (otherwise we only have the first page)
        if (issueFlow.value?.status == IssueStatus.regular) {
            viewModelScope.launch {
                updateCurrentItemInternal(getPositionOfPdf(link))
            }
        }
    }

    private suspend fun getPositionOfPdf(fileName: String): Int {
        val pdfPageList = pdfPageListFlow.firstOrNull()
        return pdfPageList?.indexOfFirst { it.pagePdf.name == fileName } ?: 0
    }

    fun onFrameLinkClicked(link: String) {
        if (link.startsWith("art") && link.endsWith(".html")) {
            showArticle(link)
        } else if (link.startsWith("http") || link.startsWith("mailto:")) {
            _openLinkEventFlow.value = OpenLinkEvent.OpenExternal(link)
        } else if (link.startsWith("s") && link.endsWith(".pdf")) {
            goToPdfPage(link)
        } else {
            val hint = "Don't know how to open $link"
            log.warn(hint)
            Sentry.captureMessage(hint)
        }
    }

    private val _openLinkEventFlow: MutableStateFlow<OpenLinkEvent?> = MutableStateFlow(null)
    val openLinkEventFlow = _openLinkEventFlow.asStateFlow()

    /**
     * Sets the article to show.
     *
     * @param link - the articles link, e.g.: art0000001.html
     */
    private fun showArticle(link: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val article = getCorrectArticle(link)
            val issueKeyWithPages = issueFlow.value?.issueKey
            if (issueKeyWithPages == null) {
                log.info("Could not show article because there is no issue selected")
                return@launch
            }

            val issueKey = IssueKey(issueKeyWithPages)

            _openLinkEventFlow.value = when {
                article != null && article.isImprint() ->
                    OpenLinkEvent.ShowImprint(IssueKeyWithDisplayableKey(issueKey, article.key))

                else -> OpenLinkEvent.ShowArticle(issueKey, article?.key)
            }
        }
    }

    /**
     * To be called immediately when any event from openLinkEventFlow has been consumed
     */
    fun linkEventIsConsumed() {
        _openLinkEventFlow.value = null
    }

    private suspend fun getCorrectArticle(link: String): Article? {
        val correctLink = if (issueFlow.value?.status == IssueStatus.regular) {
            link
        } else {
            // if we are not on a regular issue all the articles have "public" indication
            // unfortunately it is not delivered via [page.frameList] so we need to modify it here:
            link.replace(".html", ".public.html")
        }
        return articleRepository.get(correctLink)
    }

    /**
     * Flow that indicates if the subscription elapsed dialog should be shown.
     * Will not emit anything until a issue is loaded.
     */
    val showSubscriptionElapsedFlow: Flow<Boolean> = combine(
        issueFlow.filterNotNull(),
        authHelper.status.asFlow(),
        authHelper.elapsedFormAlreadySent.asFlow()
    ) { issue, authStatus, isElapsedFormAlreadySent ->
        val isPublic = issue.issueKey.status == IssueStatus.public
        val isElapsed = authStatus == AuthStatus.elapsed

        isPublic && isElapsed && !isElapsedFormAlreadySent
    }

    private suspend fun downloadIssueMetadata(
        issuePublicationWithPages: IssuePublicationWithPages,
        maxRetries: Int = METADATA_DOWNLOAD_RETRY_INDEFINITELY
    ) = contentService.downloadMetadata(
        issuePublicationWithPages,
        maxRetries = maxRetries
    ) as IssueWithPages

    private val itemsToCFlow =
        issueFlow.filterNotNull().map { issue ->
            val sortedArticlesOfIssueMap =
                issue.getArticles()
                    .map { it.key }
                    .withIndex()
                    .associate { it.value to it.index }
            if (issue.isDownloaded(application)) {
                val pages = mutableListOf<PageWithArticlesListItem>()
                var imprint: Article? = null
                issue.pageList.forEach { page ->
                    val articlesOfPage = mutableListOf<Article>()
                    page.frameList?.forEach { frame ->
                        frame.link?.let { link ->
                            if (link.startsWith("art") && link.endsWith(".html")) {
                                val article = getArticleForFrame(frame)
                                if (article != null && !isArticleListed(
                                        article,
                                        pages as List<PageWithArticlesListItem>
                                    )
                                ) {
                                    if (article.isImprint()) {
                                        imprint = article
                                    } else {
                                        articlesOfPage.add(article)
                                    }
                                }
                            }
                        }
                    }
                    // Add pages only if articles are starting on it
                    if (articlesOfPage.isNotEmpty()) {
                        val sortedArticlesOfPage =
                            articlesOfPage.sortedBy {
                                sortedArticlesOfIssueMap.getOrDefault(
                                    it.key,
                                    Int.MAX_VALUE
                                )
                            }
                        pages.add(
                            PageWithArticlesListItem.Page(
                                PageWithArticles(
                                    pagePdf = requireNotNull(
                                        fileEntryRepository.get(page.pagePdf.name)
                                    ) {
                                        "Refreshing pagePdf fileEntry failed as fileEntry was null"
                                    },
                                    page.pagina,
                                    sortedArticlesOfPage
                                )
                            )
                        )
                    }
                }
                imprint?.let { pages.add(PageWithArticlesListItem.Imprint(it)) }
                pages
            } else {
                null
            }
        }

    /**
     * Check if [article] is listed on one of the [itemsToC].
     *
     * @param article Article to check for
     * @param itemsToC items of ToC either Page or Imprint
     */
    private fun isArticleListed(
        article: Article,
        itemsToC: List<PageWithArticlesListItem>
    ): Boolean {
        return itemsToC.any { item ->
            item is PageWithArticlesListItem.Page &&
                    item.page.articles?.any { it.key == article.key } == true
        }
    }

    /**
     * Get [Article] of page [Frame].
     *
     * If no non-public article is found it tries to get the public article.
     *
     * @param frame - Frame to look for an article
     * @return [Article]
     */
    private suspend fun getArticleForFrame(frame: Frame): Article? {
        if (frame.link?.startsWith("art") == true && frame.link.endsWith(".html")) {
            val article = articleRepository.get(frame.link)
            if (article != null) {
                return article
            }

            // If users are not logged in, only the ".public.html" articles are fetched.
            // In the frame list of the first page, the non-public articles are listed
            // with their names, which are not accessible for not logged-in users.
            // To fix this, the ".public." sub string is added to the article link.
            val publicArticleName = frame.link.replace(".html", ".public.html")
            val publicArticle = articleRepository.get(publicArticleName)

            if (publicArticle == null) {
                val hint = "Could not get the public article for frame link ${frame.link}"
                log.warn(hint)
                Sentry.captureMessage(hint)
            }
            return publicArticle
        }

        return null
    }


    val itemsToC = itemsToCFlow.filterNotNull().asLiveData()

    private val currentPageFlow = combine(
        pdfPageListFlow.filterNotNull(),
        _currentItem.asFlow()
    ) { pdfPageList, currentItem ->
        pdfPageList[currentItem]
    }

    val currentPage = currentPageFlow.filterNotNull().asLiveData()

}