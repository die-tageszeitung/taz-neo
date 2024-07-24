package de.taz.app.android.ui.pdfViewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Frame
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
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
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment.Companion.getShouldShowSubscriptionElapsedDialogFlow
import de.taz.app.android.util.Log
import de.taz.app.android.sentry.SentryWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val pageRepository = PageRepository.getInstance(application.applicationContext)

    private var issuePublication: IssuePublicationWithPages? = null

    private val issueStubFlow = MutableStateFlow<IssueStub?>(null)

    val issueStubLiveData = issueStubFlow.filterNotNull().asLiveData()

    val issueStub: IssueStub?
        get() = issueStubFlow.value

    private val pdfPageListFlow: MutableStateFlow<List<Page>?> = MutableStateFlow(null)
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

        val pdfPageList = pdfPageListFlow.value
        val validPosition = position.coerceIn(0, pdfPageList?.size ?: DEFAULT_NUMBER_OF_PAGES)
        if (_currentItem.value != validPosition) {
            _currentItem.value = validPosition

            // Save current position to database to restore later on
            issueStubFlow.value?.issueKey?.let {
                issueRepository.saveLastPagePosition(
                    it,
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

                val cachedIssueKey = contentService.getIssueKey(issuePublicationWithPages)
                val isIssuePresent = contentService.isPresent(issuePublicationWithPages)

                val issueStub = if (isIssuePresent && cachedIssueKey != null) {
                    issueRepository.getStub(cachedIssueKey)
                } else {
                    // If the Issue metadata is not downloaded yet, we try to download it
                    suspend fun downloadMetadata(maxRetries: Int = -1) =
                        contentService.downloadMetadata(
                            issuePublicationWithPages, maxRetries = maxRetries
                        ) as IssueWithPages

                    val issue = try {
                        downloadMetadata(maxRetries = 3)
                    } catch (e: CacheOperationFailedException) {
                        // show error then retry infinitely
                        issueDownloadFailedErrorFlow.emit(true)
                        downloadMetadata()
                    }

                    // Start the download of the Issue content on the application scope, so that it
                    // can finish even if the user leaves the PDF pager again.
                    getApplicationScope().launch {
                        try {
                            contentService.downloadToCache(
                                download = IssuePublicationWithPages(issue.issueKey)
                            )
                        } catch (e: Exception) {
                            log.error("Failed to download full PDF issue", e)
                            issueDownloadFailedErrorFlow.emit(true)
                        }
                    }

                    issueRepository.getStub(issue.issueKey)
                }

                if (issueStub == null) {
                    val hint =
                        "Issue of ${issuePublicationWithPages.date} metadata that was just downloaded is not found in the database."
                    log.error(hint)
                    SentryWrapper.captureMessage(hint)
                    issueDownloadFailedErrorFlow.emit(true)
                    return@launch
                }

                // Update the latest page position and the viewDate
                updateCurrentItemInternal(issueStub.lastPagePosition ?: 0)
                issueRepository.updateLastViewedDate(issueStub)

                // Finally store the IssueStub, even it if is not downloaded yet
                issueStubFlow.value = issueStub

                if (issueStub.dateDownload != null && issueStub.dateDownloadWithPages != null) {
                    pdfPageListFlow.value = pageRepository.getPagesForIssueKey(issueStub.issueKey)

                } else {
                    // If the Issue is not fully downloaded we have to observer the download progress
                    // and update the pageList when new data is available.
                    handleIssueContentDownloadProgress(issueStub)
                }

            }
        } else {
            issueStubFlow.value = null
        }
    }


    private fun handleIssueContentDownloadProgress(issueStub: IssueStub) {
        viewModelScope.launch {
            // Once the Issue is fully downloaded, we have to store it on the flow, so that the
            // depending operations will start (for example itemsToC)
            issueStubFlow.value =
                issueRepository.getStubFlow(issueStub.feedName, issueStub.date, issueStub.status)
                    .first { it?.dateDownload != null && it.dateDownloadWithPages != null }

            // We could get the pages for the issue here again to be on the safe side.
            // But it does not seem to be required, so we skip it for now.
        }

        viewModelScope.launch {
            // Attention: the following code is n^2: it will get the whole list of Pages
            // every time a single Page changes. For example when it is downloaded
            val pagesFlow = pageRepository.getPagesForIssueKeyFlow(issueStub.issueKey)
            pagesFlow.collect { pages ->
                val allPagesDownloaded = pages.all { it.dateDownload != null }

                if (allPagesDownloaded) {
                    // Once all the pages are downloaded, we stop listening for database changes
                    pdfPageListFlow.value = pages
                    cancel()

                } else {
                    // As the PageStub.dateDownloaded is overwritten when the Issue.pageList is saved
                    // we hack around and look at the pdfs FileEntry dateDownloaded directly.
                    // This will allow to show the FrontPage immediately without waiting for its re-download.
                    val pagesWithFileEntryDateDownloaded = pages.map { page ->
                        if (page.dateDownload == null && page.pagePdf.dateDownload != null) {
                            page.copy(dateDownload = page.pagePdf.dateDownload)
                        } else {
                            page
                        }
                    }
                    val anyPageDownloaded =
                        pagesWithFileEntryDateDownloaded.any { it.dateDownload != null }
                    if (anyPageDownloaded) {
                        pdfPageListFlow.value = pagesWithFileEntryDateDownloaded
                    } else {
                        pdfPageListFlow.value = null
                    }
                }
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
        if (issueStubFlow.value?.status == IssueStatus.regular) {
            viewModelScope.launch {
                updateCurrentItemInternal(getPositionOfPdf(link))
            }
        }
    }

    private fun getPositionOfPdf(fileName: String): Int {
        val pdfPageList = pdfPageListFlow.value
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
            SentryWrapper.captureMessage(hint)
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
            val issueKeyWithPages = issueStubFlow.value?.issueKey
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
        val correctLink = if (issueStubFlow.value?.status == IssueStatus.regular) {
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
        issueStubFlow.filterNotNull(),
        authHelper.getShouldShowSubscriptionElapsedDialogFlow()
    ) { issue, shouldShowSubscriptionElapsedDialog ->
        val isPublic = issue.issueKey.status == IssueStatus.public
        isPublic && shouldShowSubscriptionElapsedDialog
    }

    private val itemsToCFlow =
        issueStubFlow.filterNotNull().map { issueStub ->
            if (issueStub.isDownloaded(application)) {
                val sortedArticlesOfIssueMap = articleRepository.getArticleListForIssue(issueStub.issueKey)
                    .map { it.key }
                    .withIndex()
                    .associate { it.value to it.index }
                val pages = mutableListOf<PageWithArticlesListItem>()
                var imprint: Article? = null
                pageRepository.getPagesForIssueKey(issueStub.issueKey).forEach { page ->
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
                SentryWrapper.captureMessage(hint)
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
        pdfPageList.getOrNull(currentItem)
    }

    val currentPage = currentPageFlow.filterNotNull().asLiveData()

}