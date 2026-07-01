package de.taz.app.android.ui.pdfViewer

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.taz.app.android.ADVERTISEMENT_URL_STRING
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Frame
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.api.models.Page
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.monkey.isArticleKey
import de.taz.app.android.monkey.isPageKey
import de.taz.app.android.monkey.isSectionKey
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment.Companion.getShouldShowSubscriptionElapsedDialogFlow
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KEY_CURRENT_ITEM = "KEY_CURRENT_ITEM"

data class PageWithArticles(
    val pagePdf: FileEntry,
    val pagina: String?,
    val title: String?,
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
    private val generalDataStore = GeneralDataStore.getInstance(application.applicationContext)
    private val pageRepository = PageRepository.getInstance(application.applicationContext)
    private val toastHelper = ToastHelper.getInstance(application.applicationContext)
    private val tracker = Tracker.getInstance(application.applicationContext)

    private var issuePublication: IssuePublicationWithPages? = null

    private val issueStubFlow = MutableStateFlow<IssueStub?>(null)
    val continueReadDisplayable = MutableStateFlow<IssueKeyWithDisplayableKey?>(null)

    val issueStubLiveData = issueStubFlow.filterNotNull().asLiveData()

    val issueStub: IssueStub?
        get() = issueStubFlow.value

    @OptIn(ExperimentalCoroutinesApi::class)
    val pdfPageListFlow: StateFlow<List<Page>> = issueStubFlow.flatMapLatest {
        it?.issueKey?.let {
            issueKey -> pageRepository.getPagesForIssueKeyFlow(issueKey)
        } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val issueDownloadFailedErrorFlow = MutableStateFlow(false)

    private val _currentItem = savedStateHandle.getLiveData<Int>(KEY_CURRENT_ITEM)
    val currentItem = _currentItem as LiveData<Int>

    fun updateCurrentItem(position: Int) {
        viewModelScope.launch {
            updateCurrentItemInternal(position)
        }
    }

    private suspend fun updateCurrentItemInternal(
        position: Int,
        saveLastDisplayable: Boolean = true
    ) = withContext(Dispatchers.Main) {
        if (_currentItem.value == position) {
            return@withContext
        }

        // wait for pdfPageList to be initialized
        val pdfPageList = pdfPageListFlow.first { it.isNotEmpty() }

        val validPosition = position.coerceIn(0, pdfPageList.size - 1)

        if (_currentItem.value != validPosition) {
            _currentItem.value = validPosition

            if (saveLastDisplayable) {
                // Save current position to database to restore later on
                issueStubFlow.value?.issueKey?.let {
                    issueRepository.saveLastPagePosition(
                        it,
                        validPosition
                    )
                    // Use lastDisplayable to also store the page number. It is necessary for the
                    // continue read functionality to distinguish between  page and article
                    pdfPageList.getOrNull(validPosition)?.pagePdf?.name?.let { fileName ->
                        issueRepository.saveLastDisplayable(it, fileName)
                    }
                }
            }
        }
    }

    fun setIssuePublication(
        issuePublication: IssuePublicationWithPages,
        continueReadDirectly: Boolean = false,
        displayableKey: String? = null,
    ) {
        require(this.issuePublication == null || this.issuePublication == issuePublication) {
            "Each PdfPagerViewModel instance may only be used for exactly one issue. Updating it to another is not allowed."
        }

        if (this.issuePublication == null) {
            // Reload the issue if a new publication is set
            this.issuePublication = issuePublication
            loadIssue(continueReadDirectly, displayableKey)
        }
    }

    private val _reloadPdfFlow = MutableSharedFlow<Unit>()
    val reloadPdfFlow = _reloadPdfFlow.asSharedFlow()
    fun refresh() {
        viewModelScope.launch {
            _reloadPdfFlow.emit(Unit)
        }
    }

    private fun loadIssue(continueReadDirectly: Boolean = false, displayableKey: String? = null) {
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
                            issuePublicationWithPages, maxRetries = maxRetries, allowCache = false
                        ) as IssueWithPages

                    val issue = try {
                        downloadMetadata(maxRetries = 3)
                    } catch (_: CacheOperationFailedException) {
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

                // Check for updates in the background if the issue is already present.
                if (isIssuePresent && cachedIssueKey != null) {
                    viewModelScope.launch {
                        if (!contentService.issueIsUpToDate(cachedIssueKey)) {
                            toastHelper.showToast(R.string.toast_loading_new_issue_from_server, true)
                            // Redownload the issue in background and refresh when loaded
                            getApplicationScope().launch {
                                try {
                                    contentService.downloadToCache(
                                        issuePublicationWithPages, allowCache = false
                                    )
                                    refresh()
                                } catch (e: Exception) {
                                    log.error("Failed to download updated PDF issue. Showing old cached issue instead", e)
                                    SentryWrapper.captureException(e)
                                }
                            }
                        }
                    }
                }

                if (issueStub == null) {
                    val hint =
                        "Issue of ${issuePublicationWithPages.date} metadata that was just downloaded is not found in the database."
                    log.error(hint)
                    SentryWrapper.captureMessage(hint)
                    issueDownloadFailedErrorFlow.emit(true)
                    return@launch
                }

                // get last displayable
                val lastDisplayable = displayableKey ?: issueStub.lastDisplayableName
                val lastPage = issueStub.lastPagePosition

                val continueReadAutomatically = generalDataStore.settingsContinueRead.get()

                // Update latest view date
                issueRepository.updateLastViewedDate(issueStub)

                // Finally store the IssueStub, even it if is not downloaded yet
                issueStubFlow.value = issueStub

                // Update the latest page position if continueReadAutomatically is set
                val lastPagePosition = if (continueReadAutomatically || continueReadDirectly) {
                    lastPage ?: 0
                } else {
                    0
                }
                updateCurrentItemInternal(lastPagePosition)

                // Check for displayable and (maybe) show continue read bottom sheet
                val askEachTime = generalDataStore.settingsContinueReadAskEachTime.get()
                val isArticle = lastDisplayable?.isArticleKey() == true
                val isSection = lastDisplayable?.isSectionKey() == true
                val isPage = lastDisplayable?.isPageKey() == true
                val isFirstPage = lastDisplayable == pdfPageListFlow.value.firstOrNull()?.pagePdf?.name
                val continueRead = (continueReadDirectly || continueReadAutomatically) && lastDisplayable != null

                // If continueReadAutomatically setting is set and last displayable is article
                if (continueRead && isArticle) {
                    showArticle(lastDisplayable)
                // Otherwise check whether to show continue read bottom sheet
                } else if (continueRead || (askEachTime && (isArticle || isPage) && !isFirstPage && !isSection)) {
                    log.debug("Show continue read bottom sheet with displayable $lastDisplayable")
                    val issueKeyWithDisplayable = IssueKeyWithDisplayableKey(
                        issueStub.issueKey,
                        lastDisplayable
                    )
                    continueReadDisplayable.value = issueKeyWithDisplayable
                }
            }
        } else {
            issueStubFlow.value = null
        }
    }

    fun goToPdfPage(link: String, saveLastDisplayable: Boolean = true) {
        // it is only possible to go to another page if we are on a regular issue
        // (otherwise we only have the first page)
        if (issueStubFlow.value?.status == IssueStatus.regular) {
            viewModelScope.launch {
                updateCurrentItemInternal(getPositionOfPdf(link), saveLastDisplayable)
            }
        }
    }

    private fun getPositionOfPdf(fileName: String): Int {
        val pdfPageList = pdfPageListFlow.value
        return pdfPageList.indexOfFirst { it.pagePdf.name == fileName }
    }

    suspend fun onFrameLinkClicked(link: String) {
        if (link.isArticleKey()) {
            if(generalDataStore.openArticlePdfView.get()) {
                showArticle(link)
            }
        } else if (link.startsWith("http") || link.startsWith("mailto:")) {
            handleIfAd(link)
            _openLinkEventFlow.value = OpenLinkEvent.OpenExternal(link)
        } else if (link.isPageKey()) {
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
    fun showArticle(link: String, givenIssueKey: IssueKey? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            val issueKeyWithPages = givenIssueKey ?: issueStubFlow.value?.issueKey
            if (issueKeyWithPages == null) {
                log.warn("Could not show article because there is no issue selected")
                return@launch
            }
            val article = getCorrectArticle(link)

            val issueKey = IssueKey(issueKeyWithPages)

            // if we got a givenIssueKey we probably are not coming from the articles' page, so
            // we update the pdfPager page here:
            if (givenIssueKey != null) {
                article?.pageNameList?.firstOrNull()?.let {
                    goToPdfPage(it, saveLastDisplayable = false)
                }
            }

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
        val publicLink = link.replace(".html", ".public.html")
        // If we do not have regular article (try to) get the public one
        val article = articleRepository.get(link) ?: articleRepository.get(publicLink)

        return article
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
                            if (link.isArticleKey()) {
                                val article = getArticleForFrame(frame)
                                val articleBeginsHere = articleBeginsOnPage(article, page)
                                val articleNotListed = !isArticleListed(
                                    article,
                                    pages as List<PageWithArticlesListItem>
                                )
                                val showArticleOnThisPage =
                                    (BuildConfig.IS_LMD || articleBeginsHere) && articleNotListed
                                if (article != null && showArticleOnThisPage) {
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
                                if (articlesOfPage.isNotEmpty()) page.pagina else null,
                                page.title,
                                sortedArticlesOfPage
                            )
                        )
                    )
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
        article: Article?,
        itemsToC: List<PageWithArticlesListItem>
    ): Boolean {
        return itemsToC.any { item ->
            item is PageWithArticlesListItem.Page &&
                    item.page.articles?.any { it.key == article?.key } == true
        }
    }

    private fun articleBeginsOnPage(article: Article?, page: Page): Boolean {
        return page.pagePdf.name == article?.pageNameList?.firstOrNull()
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
        if (frame.link?.isArticleKey() == true) {
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

    /**
     * if the link contains "dl.taz.de/anzstat" it an advertisement url, eg:
     * "https://dl.taz.de/anzstat?url=https://www.byte.fm/sendungen/tazmixtape/&eTag=2025-11-28&pub=taz&Typ=tApp&id=byteFM_bln_320066"
     * The `adId` is the substring behind "&id=", eg: "byteFM_bln_320066"
     */
    private fun handleIfAd(url: String) {
        if (ADVERTISEMENT_URL_STRING !in url) return
        val uri = url.toUri()
        val adId = uri.getQueryParameter("id")
        log.debug("Track ad tapped $adId")
        tracker.trackAdTapped(adId ?: url)
    }

    val itemsToC = itemsToCFlow.filterNotNull().asLiveData()

}