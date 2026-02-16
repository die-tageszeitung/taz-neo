package de.taz.app.android.ui.webview

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.ViewGroup
import android.view.ViewParent
import android.view.WindowInsets
import android.webkit.WebSettings
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.monkey.getVisibleHeight
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.ViewerStateRepository
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.CannotDetermineBaseUrlException
import de.taz.app.android.singletons.DEFAULT_COLUMN_GAP_PX
import de.taz.app.android.singletons.DEFAULT_FONT_SIZE
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.ViewBorder
import de.taz.app.android.ui.bookmarks.BookmarkViewerActivity
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.util.getBottomNavigationBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.max

private const val SAVE_SCROLL_POS_DEBOUNCE_MS = 100L
private const val TAP_LOCK_JS_DELAY_MS = 50L
private const val TAP_LOCK_DELAY_MS = 500L

@Retention(AnnotationRetention.SOURCE)
@IntDef(SCROLL_BACKWARDS, SCROLL_FORWARD)
private annotation class ScrollDirection

private const val SCROLL_FORWARD = 1
private const val SCROLL_BACKWARDS = -1

abstract class WebViewFragment<
        DISPLAYABLE : WebViewDisplayable,
        VIEW_MODEL : WebViewViewModel<DISPLAYABLE>,
        VIEW_BINDING : ViewBinding,
        > : BaseViewModelFragment<VIEW_MODEL, VIEW_BINDING>(),
    AppWebViewClientCallBack, MultiColumnLayoutReadyCallback {

    abstract override val viewModel: VIEW_MODEL

    val restoreScrollPositionViewModel by viewModels<RestoreScrollPositionViewModel>(ownerProducer = {
        // we need to create the viewModel in the scope of the IssueViewerWrapperFragment
        // as we need to ensure we listen to the correct continueReadClicked event
        // triggered by the BottomSheet
        requireParentFragment().requireParentFragment()
    })

    protected val log by Log

    private lateinit var storageService: StorageService
    private lateinit var contentService: ContentService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var tracker: Tracker
    private lateinit var viewerStateRepository: ViewerStateRepository
    private lateinit var generalDataStore: GeneralDataStore

    protected var isRendered = false

    private var saveScrollPositionJob: Job? = null

    private var currentIssueKey: IssueKey? = null
    private var currentDisplayableKey: String? = null

    val preventTap = AtomicBoolean(false)
    val tryingToTap = AtomicBoolean(false)

    val issueViewerViewModel: IssueViewerViewModel by activityViewModels()
    val helpFabViewModel: HelpFabViewModel by activityViewModels()

    abstract suspend fun reloadAfterCssChange()

    abstract val webView: AppWebView?
    abstract val loadingScreen: View?

    private var webViewInnerWidth: Int? = null
    private var paddingAdded = false
    private var tapToScroll = false
    private var multiColumnMode = false

    // When scrolling programmatically, the nested scrolling events are not triggered.
    // We have to collapse/hide the bar manually, when tapToScroll is used.
    // Must be null if there is no collapsible app bar in the WebViewFragment implementations view.
    abstract val appBarLayout: AppBarLayout?
    abstract val bottomNavigationLayout: View?

    override fun onAttach(context: Context) {
        super.onAttach(context)
        contentService = ContentService.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        viewerStateRepository =
            ViewerStateRepository.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.Default) {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.displayableFlow
                    .filterNotNull()
                    .filter { it.key != currentDisplayableKey }
                    .onEach { displayable ->
                        currentDisplayableKey = displayable.key
                        log.debug("Received a new displayable ${displayable.key}")
                        setHeader(displayable)
                        currentIssueKey =
                            displayable.getIssueStub(requireContext().applicationContext)?.issueKey
                        ensureDownloadedAndShow()
                    }.launchIn(lifecycleScope)

                generalDataStore.hideAppbarOnScroll.asFlow().onEach {
                    webView?.setCoordinatorBottomMatchingBehaviourEnabled(it)
                }.launchIn(lifecycleScope)

                viewModel.tapToScrollFlow
                    .onEach {
                        tapToScroll = it
                    }.launchIn(lifecycleScope)

                viewModel.reloadCssFlow
                    .drop(1)
                    .onEach {
                        reloadAfterCssChange()
                    }.launchIn(lifecycleScope)

                viewModel.multiColumnModeFlow
                    .onEach {
                        multiColumnMode = it
                        setupScrollPositionListener(it)
                    }.launchIn(lifecycleScope)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState != null) {
            appBarLayout?.setExpanded(true, false)
        }

        configureWebView()
    }

    override fun onMultiColumnLayoutReady(contentWidth: Int?) {
        if (contentWidth != null) {
            // The inner width of the web view is the sum of:
            // + the contentWidth we receive from the tazApi.js
            // + 2 times the column gap (at the very left and the very right)
            // That need to be multiplied by the density so we have it in our "dp" value:
            webViewInnerWidth =
                ((contentWidth + 2 * DEFAULT_COLUMN_GAP_PX) * resources.displayMetrics.density).toInt()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun configureWebView() {

        webView?.apply {
            webViewClient = AppWebViewClient(
                requireContext().applicationContext,
                this@WebViewFragment
            )
            webChromeClient = AppWebChromeClient(::onPageRendered)

            // Sometimes the webview wasn't rendered fully (only api above 30).
            // Enabling hardware acceleration seems to fix it:
            if (Build.VERSION.SDK_INT >= 30) {
                setLayerType(LAYER_TYPE_HARDWARE, null)
            }

            settings.apply {
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                domStorageEnabled = true
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            onBorderTapListener = { border ->
                when (border) {
                    ViewBorder.LEFT -> {
                        maybeScroll(SCROLL_BACKWARDS)
                    }

                    ViewBorder.RIGHT -> {
                        maybeScroll(SCROLL_FORWARD)
                    }

                    else -> false
                }
            }

            // For tablets the bottom navigation layout does not collapse, so we need
            // extra margin here, so the content won' be behind the nav bar
            addBottomMarginIfNecessary()

            addJavascriptInterface(TazApiJS(this@WebViewFragment), TAZ_API_JS)
            setBackgroundColor(ContextCompat.getColor(context, R.color.backgroundColor))
        }
    }


    private fun setupScrollPositionListener(isMultiColumnMode: Boolean) {
        if (isMultiColumnMode) {
            webView?.scrollListener = object : AppWebView.WebViewScrollListener {
                override fun onScroll(
                    scrollX: Int,
                    scrollY: Int,
                    oldScrollX: Int,
                    oldScrollY: Int
                ) {
                    saveScrollPositionDebounced(scrollPositionHorizontal = scrollX)
                    handleDrawerLogoOnVerticalScroll(scrollX, oldScrollX)
                }
            }
        } else {
            webView?.scrollListener = object : AppWebView.WebViewScrollListener {
                override fun onScroll(
                    scrollX: Int,
                    scrollY: Int,
                    oldScrollX: Int,
                    oldScrollY: Int
                ) {
                    saveScrollPositionDebounced(scrollPosition = scrollY)

                    if (oldScrollY < scrollY) {
                        try {
                            val isScrolledToBottom =
                                (webView?.bottom ?: 0) <= (webView?.height ?: (0 + scrollY))
                            if (isScrolledToBottom) {
                                onScrolledToBottom()
                            }
                        } catch (npe: NullPointerException) {
                            log.warn("We lost the viewBindings web view. Abort scrolling…")
                            SentryWrapper.captureException(npe)
                        }
                    }
                }
            }

            webView?.overScrollListener = object : AppWebView.WebViewOverScrollListener {
                override fun onOverScroll(
                    scrollX: Int,
                    scrollY: Int,
                    clampedX: Boolean,
                    clampedY: Boolean
                ) {
                    if (scrollY > 0 && clampedY) {
                        onScrolledToBottom()
                    }
                }
            }
        }
    }

    open fun handleDrawerLogoOnVerticalScroll(scrollX: Int, oldScrollX: Int) = Unit
    open fun onScrolledToBottom() = Unit

    private fun saveScrollPositionDebounced(
        scrollPosition: Int = 0,
        scrollPositionHorizontal: Int = 0
    ) {
        viewModel.displayable?.let {
            val oldJob = saveScrollPositionJob
            saveScrollPositionJob = lifecycleScope.launch {
                oldJob?.cancelAndJoin()
                delay(SAVE_SCROLL_POS_DEBOUNCE_MS)
                viewModel.scrollPosition = scrollPosition
                viewModel.scrollPositionHorizontal = scrollPositionHorizontal
                viewerStateRepository.save(
                    it.key,
                    scrollPosition,
                    scrollPositionHorizontal,
                )
            }
        }
    }

    private suspend fun scrollToDirection(horizontal: Boolean, @ScrollDirection direction: Int) {
        if (horizontal) scrollHorizontally(direction)
        else scrollVertically(direction)
    }

    /**
     * scroll article horizontally.
     * If at the top or the end - go to previous or next article
     */
    private fun scrollHorizontally(@ScrollDirection direction: Int) {
        findParentViewPager()?.apply {
            isUserInputEnabled = false
            requestDisallowInterceptTouchEvent(true)
        }
        webView?.apply {
            touchDisabled = true

            if (canScrollHorizontally(direction)) {
                val webViewWidth = width
                val gap = (DEFAULT_COLUMN_GAP_PX * resources.displayMetrics.density).toInt()
                val scrollXWithBuffer = scrollX + gap
                val scrollBy = webViewWidth - gap

                val targetScrollX = if (direction == SCROLL_FORWARD)
                    (scrollXWithBuffer / scrollBy + 1) * scrollBy
                else
                    max(0, (scrollXWithBuffer / scrollBy - 1) * scrollBy)

                // Check if scrolling would overscroll - if so add padding
                if (webViewInnerWidth != null && direction == SCROLL_FORWARD) {
                    val articleWidth = webViewInnerWidth!!

                    val targetWidth = targetScrollX + webViewWidth
                    val isOverscroll = targetWidth > articleWidth
                    if (!paddingAdded && isOverscroll) {
                        val overScroll = targetWidth - articleWidth
                        val paddingToAdd =
                            floor(overScroll / resources.displayMetrics.density).toInt()
                        callTazApi(
                            "setPaddingRight",
                            paddingToAdd
                        )
                        paddingAdded = true
                    }
                }
                val scrollAnimation = ObjectAnimator.ofInt(
                    this,
                    "scrollX",
                    scrollX,
                    targetScrollX
                )
                scrollAnimation.start()
            } else {
                scrollToNextItem(direction)
            }
            lifecycleScope.launch {
                delay(TAP_LOCK_DELAY_MS)
                touchDisabled = false
                findParentViewPager()?.apply {
                    isUserInputEnabled = true
                    requestDisallowInterceptTouchEvent(false)
                }
            }
        }
    }

    private fun scrollToNextItem(direction: Int) {
        findParentViewPager()?.let { viewPager ->
            val nextItem = viewPager.currentItem + direction
            viewPager.setCurrentItem(nextItem, true)
        }
    }

    private var viewPagerCache: ViewPager2? = null

    private fun findParentViewPager(): ViewPager2? {
        if (viewPagerCache != null) return viewPagerCache

        var current: ViewParent? = webView
        while (current?.parent != null) {
            current = current.parent
            if (current is ViewPager2) {
                viewPagerCache = current
                return current
            }
        }
        return null
    }

    /**
     * scroll article into [direction]. If at the top or the end - go to previous or next article
     */
    private suspend fun scrollVertically(@ScrollDirection direction: Int) {
        val webView = this.webView ?: return

        // if on bottom and bottom bar is hidden tap on right side go to next article
        if (!webView.canScrollVertically(SCROLL_FORWARD) && direction == SCROLL_FORWARD
            && (bottomNavigationLayout?.getVisibleHeight() == 0 || bottomNavigationLayout?.getBottomNavigationBehavior() == null)) {
            issueViewerViewModel.goNext.emit(Unit)
        }

        // if on top and tap on left side go to previous article
        else if (!webView.canScrollVertically(SCROLL_BACKWARDS) && direction == SCROLL_BACKWARDS) {
            issueViewerViewModel.goPrevious.emit(Unit)
        } else {
            val appBarLayout = this.appBarLayout
            val bottomNavigationLayout = this.bottomNavigationLayout
            val bottomNavigationBehavior = bottomNavigationLayout?.getBottomNavigationBehavior()

            var visibleBottom = resources.displayMetrics.heightPixels
            var targetTop =
                resources.getDimensionPixelSize(R.dimen.fragment_webview_tap_to_scroll_offset)

            // Keep a 1 line overlap so that the last/first line is visible after scrolling
            // It is defined by the font size and line height (1.33rem)
            val lineHeightSp =
                tazApiCssDataStore.fontSize.get().toInt() * 0.0133 * DEFAULT_FONT_SIZE
            val lineHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                lineHeightSp.toFloat(),
                resources.displayMetrics
            ).toInt()

            // Since API 35 content is drawn edge to edge
            if (Build.VERSION.SDK_INT >= 35) {
                // Let 1 line be visible for better orientation when app bar
                // is expanded and not drawn behind status bar (api 35+)
                if (appBarLayout != null && appBarLayout.bottom > 0) {
                    targetTop += lineHeight
                }
                // And remove the navigation bar from the bottom
                visibleBottom -= getNavigationBarHeight()
            }

            if (bottomNavigationLayout != null) {
                if (bottomNavigationBehavior != null) {
                    // If the bottom navigation is shown, the visible content bottom is higher up
                    visibleBottom -= bottomNavigationLayout.getVisibleHeight()
                    bottomNavigationBehavior.slideDown(bottomNavigationLayout, true)
                } else {
                    // If the bottom navigation does not have a behavior, it is expanded
                    visibleBottom -= bottomNavigationLayout.height
                }
            }

            val scrollDelta = visibleBottom - targetTop
            var scrollDestination = 0

            when (direction) {
                SCROLL_FORWARD -> {
                    helpFabViewModel.hideHelpFab()
                    scrollDestination = webView.scrollY + scrollDelta
                }

                SCROLL_BACKWARDS -> {
                    helpFabViewModel.showHelpFab()
                    scrollDestination = webView.scrollY - scrollDelta
                }
            }
            val scrollAnimation = ObjectAnimator.ofInt(
                webView,
                "scrollY",
                webView.scrollY,
                scrollDestination
            )
            scrollAnimation.start()
        }
    }

    fun getNavigationBarHeight(): Int {
        val rootWindowInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view?.rootWindowInsets
        } else {
            null
        }

        val navigationBarHeight =
            if (rootWindowInsets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                rootWindowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                resources.getDimensionPixelSize(
                    resources.getIdentifier("navigation_bar_height", "dimen", "android")
                )
            }

        return navigationBarHeight
    }

    abstract fun setHeader(displayable: DISPLAYABLE)

    /**
     * Setup the handling for the bookmarks in the current webview.
     * It must return a list of all bookmarked Article names to be used for the initial state.
     *
     * Return a List of bookmarked Article names (without the .html suffix) for the current webview.
     * The context is usually a Section - otherwise the behavior is not defined.
     */
    open suspend fun setupBookmarkHandling(articleNamesInWebView: List<String>): List<String> =
        emptyList()

    open suspend fun onSetBookmark(
        articleName: String,
        isBookmarked: Boolean,
        showNotification: Boolean
    ) = Unit

    /**
     * Setup the handling for the playlist in the current webview.
     * It must return a list of all Article names enqueued in the initial state.
     *
     * Return a List of enqueued Article names (without the .html suffix) for the current webview.
     * The context is usually a Section - otherwise the behavior is not defined.
     */
    open suspend fun setupEnqueuedHandling(articleNamesInWebView: List<String>): List<String> =
        emptyList()

    open suspend fun onEnqueued(articleName: String, isEnqueued: Boolean) = Unit


    open fun onPageRendered() {
        isRendered = true
    }

    override fun onPageFinishedLoading() {
        // do nothing instead use onPageRendered
    }

    override fun onResume() {
        preventTap.set(false)
        tryingToTap.set(false)
        super.onResume()
    }

    open fun hideLoadingScreen() {
        activity?.runOnUiThread {
            try {
                loadingScreen?.animate()?.alpha(0f)?.duration = LOADING_SCREEN_FADE_OUT_TIME
            } catch (npe: NullPointerException) {
                log.error("Tried to access loading screen which is already closed.")
                SentryWrapper.captureException(npe)
            }
        }
    }

    private suspend fun loadUrl(url: String) = withContext(Dispatchers.Main) {
        webView?.loadUrl(url)
    }

    override fun onDestroyView() {
        webView?.destroy()
        super.onDestroyView()
    }

    private suspend fun ensureDownloadedAndShow() {
        viewModel.displayable?.let { displayable ->
            log.info("Displayable is ${displayable.key}")
            try {
                contentService.downloadToCache(displayable, priority = DownloadPriority.High)
                val displayableFile = fileEntryRepository.get(displayable.key)
                val path = displayableFile?.let {
                    storageService.getFileUri(it)
                }
                path?.let { loadUrl(it) }
            } catch (_: CacheOperationFailedException) {
                issueViewerViewModel.issueLoadingFailedErrorFlow.emit(true)
            } catch (e: CannotDetermineBaseUrlException) {
                // FIXME (johannes): Workaround to #14367
                // concurrent download/deletion jobs might result in a articles missing their parent issue and thus not being able to find the base url
                log.warn("Could not determine baseurl for the displayable ${displayable.key}", e)
                SentryWrapper.captureException(e)
                issueViewerViewModel.issueLoadingFailedErrorFlow.emit(true)
            }
        }
    }

    override fun onLinkClicked(displayableKey: String) {
        if (!tryingToTap.get()) {
            setDisplayable(displayableKey)
        }
    }

    fun setDisplayable(displayableKey: String, linkClicked: Boolean = false) {
        currentIssueKey?.let {
            lifecycleScope.launch {
                if (activity is BookmarkViewerActivity && linkClicked) {
                    (activity as BookmarkViewerActivity).showDisplayable(it, displayableKey)
                } else {
                    issueViewerViewModel.setDisplayable(it, displayableKey)
                }
            }
        }
    }

    suspend fun restoreLastScrollPosition() {
        viewModel.displayable?.let {
            val persistedScrollPosition = viewerStateRepository.get(it.key)?.scrollPosition
            viewModel.scrollPosition = persistedScrollPosition ?: viewModel.scrollPosition
        }
        viewModel.scrollPosition?.let {
            webView?.scrollY = it
        } ?: run {
            appBarLayout?.setExpanded(true, false)
        }
    }

    // TODO fix scroll positions
    suspend fun restoreLastHorizontalScrollPosition() {
        try {
            viewModel.displayable?.let {
                val persistedScrollPosition =
                    viewerStateRepository.get(it.key)?.scrollPositionHorizontal
                viewModel.scrollPositionHorizontal =
                    persistedScrollPosition ?: viewModel.scrollPositionHorizontal
            }
            viewModel.scrollPositionHorizontal?.let {
                if (it > 0) {
                    val scrollAnimation = ObjectAnimator.ofInt(
                        webView,
                        "scrollX",
                        0,
                        it
                    )
                    scrollAnimation.start()
                }
            }
        } catch (npe: NullPointerException) {
            log.warn("We lost the viewBindings web view. Abort horizontal scrolling…")
            SentryWrapper.captureException(npe)
        }
    }

    private fun maybeScroll(@ScrollDirection direction: Int): Boolean {
        if ((tapToScroll || multiColumnMode) && view != null) {
            tryingToTap.set(true)
            lifecycleScope.launch {
                // wait some delay to let javascript maybe handle bookmarks/links
                delay(TAP_LOCK_JS_DELAY_MS)
                // Maybe tapLock was set from setBookmark in tasApiJs, so check again:
                if (preventTap.compareAndSet(false, true)) {
                    scrollToDirection(multiColumnMode, direction)
                    // wait some delay to prevent javascript from opening links
                    delay(TAP_LOCK_DELAY_MS)
                }
                preventTap.set(false)
                tryingToTap.set(false)
            }
            return true
        }
        return false
    }

    fun addBottomMarginIfNecessary() {
        val isTablet = resources.getBoolean(R.bool.isTablet)
        if (isTablet && !multiColumnMode && webView?.marginBottom == 0) {
            val heightOfToolBar = bottomNavigationLayout?.height ?: 0
            webView?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = heightOfToolBar
            }
        }
    }

    override fun onExternalLinkClicked(context: Context, uri: Uri) {
        if (!tryingToTap.get()) {
            super.onExternalLinkClicked(context, uri)
        }
    }
}