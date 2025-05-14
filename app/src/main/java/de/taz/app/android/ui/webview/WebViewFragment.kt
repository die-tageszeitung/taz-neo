package de.taz.app.android.ui.webview

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.ViewerStateRepository
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.CannotDetermineBaseUrlException
import de.taz.app.android.singletons.DEFAULT_COLUMN_GAP_PX
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.ViewBorder
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.util.getBottomNavigationBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max

private const val SAVE_SCROLL_POS_DEBOUNCE_MS = 100L
private const val TAP_LOCK_DELAY_MS = 500L

@Retention(AnnotationRetention.SOURCE)
@IntDef(SCROLL_BACKWARDS, SCROLL_FORWARD)
private annotation class ScrollDirection

private const val SCROLL_FORWARD = 1
private const val SCROLL_BACKWARDS = -1

// Only log 0 height status bars to sentry once per app start.
// FIXME (johannes): this global variable should be part of an explicit helper
private var sentryLoggedStatusBarHeight0 = false

abstract class WebViewFragment<
        DISPLAYABLE : WebViewDisplayable,
        VIEW_MODEL : WebViewViewModel<DISPLAYABLE>,
        VIEW_BINDING : ViewBinding,
        > : BaseViewModelFragment<VIEW_MODEL, VIEW_BINDING>(),
    AppWebViewClientCallBack, MultiColumnLayoutReadyCallback {

    abstract override val viewModel: VIEW_MODEL

    protected val log by Log

    private lateinit var storageService: StorageService
    private lateinit var contentService: ContentService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var viewerStateRepository: ViewerStateRepository

    protected var isRendered = false

    private var saveScrollPositionJob: Job? = null

    private var currentIssueKey: IssueKey? = null

    var tapLock = false

    val issueViewerViewModel: IssueViewerViewModel by activityViewModels()

    abstract fun reloadAfterCssChange()

    abstract val webView: AppWebView
    abstract val loadingScreen: View

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
        viewerStateRepository =
            ViewerStateRepository.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.displayableLiveData.distinctUntilChanged().observe(this) { displayable ->
            if (displayable != null) {
                setHeader(displayable)
            }
        }
        viewModel.nightModeLiveData.observe(this@WebViewFragment) {
            reloadAfterCssChange()
        }
        viewModel.tapToScrollLiveData.observe(this@WebViewFragment) {
            tapToScroll = it
        }
        viewModel.fontSizeLiveData.observe(this@WebViewFragment) {
            reloadAfterCssChange()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.displayableLiveData.distinctUntilChanged().observe(viewLifecycleOwner) {
            if (it != null) {
                log.debug("Received a new displayable ${it.key}")
                lifecycleScope.launch {
                    currentIssueKey = it.getIssueStub(requireContext().applicationContext)?.issueKey
                    configureWebView()
                    ensureDownloadedAndShow()
                }
            }
        }

        viewModel.multiColumnModeLiveData.observe(viewLifecycleOwner) {
            multiColumnMode = it
            setupScrollPositionListener(it)
        }

        if (savedInstanceState != null) {
            appBarLayout?.setExpanded(true, false)
        }
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
    private suspend fun configureWebView() = withContext(Dispatchers.Main) {

        webView.apply {
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
            webView.scrollListener = object: AppWebView.WebViewScrollListener {
                                override fun onScroll(
                    scrollX: Int,
                    scrollY: Int,
                    oldScrollX: Int,
                    oldScrollY: Int
                ) {
                    saveScrollPositionDebounced(scrollPositionHorizontal = scrollX)
                }
            }
        } else {
            webView.scrollListener = object: AppWebView.WebViewScrollListener {
                override fun onScroll(
                    scrollX: Int,
                    scrollY: Int,
                    oldScrollX: Int,
                    oldScrollY: Int
                ) {
                    saveScrollPositionDebounced(scrollPosition = scrollY)

                    if (oldScrollY < scrollY) {
                        try {
                            val isScrolledToBottom = webView.bottom <= (webView.height + scrollY)
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

            webView.overScrollListener = object : AppWebView.WebViewOverScrollListener {
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
        webView.touchDisabled = true

        if (webView.canScrollHorizontally(direction)) {
            val webViewWidth = webView.width
            val gap = (DEFAULT_COLUMN_GAP_PX * resources.displayMetrics.density).toInt()
            val scrollXWithBuffer = webView.scrollX + gap
            val scrollBy = webViewWidth - gap

            val targetScrollX = if(direction == SCROLL_FORWARD)
                (scrollXWithBuffer/scrollBy + 1) * scrollBy
            else
                max(0, (scrollXWithBuffer/scrollBy - 1) * scrollBy)

            // Check if scrolling would overscroll - if so add padding
            if (webViewInnerWidth != null && direction == SCROLL_FORWARD) {
                val articleWidth = webViewInnerWidth!!

                val targetWidth = targetScrollX + webViewWidth
                val isOverscroll = targetWidth > articleWidth
                if (!paddingAdded && isOverscroll) {
                    val overScroll = targetWidth - articleWidth
                    val paddingToAdd = floor(overScroll / resources.displayMetrics.density).toInt()
                    webView.callTazApi(
                        "setPaddingRight",
                        paddingToAdd
                    )
                    paddingAdded = true
                }
            }
            val scrollAnimation = ObjectAnimator.ofInt(
                webView,
                "scrollX",
                webView.scrollX,
                targetScrollX
            )
            scrollAnimation.start()
        } else {
            scrollToNextItem(direction)
        }
        lifecycleScope.launch {
            delay(TAP_LOCK_DELAY_MS)
            webView.touchDisabled = false
            findParentViewPager()?.apply {
                isUserInputEnabled = true
                requestDisallowInterceptTouchEvent(false)
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

        var current: ViewParent = webView
        while (current.parent != null) {
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
        // if on bottom and tap on right side go to next article
        if (!webView.canScrollVertically(SCROLL_FORWARD) && direction == SCROLL_FORWARD) {
            issueViewerViewModel.goNextArticle.emit(true)
        }

        // if on bottom and tap on right side go to previous article
        else if (!webView.canScrollVertically(SCROLL_BACKWARDS) && direction == SCROLL_BACKWARDS) {
            issueViewerViewModel.goPreviousArticle.emit(true)

        } else {
            val appBarLayout = this.appBarLayout
            val bottomNavigationLayout = this.bottomNavigationLayout
            val bottomNavigationBehavior = bottomNavigationLayout?.getBottomNavigationBehavior()

            when (direction) {
                SCROLL_FORWARD -> {
                    var visibleBottom = webView.height
                    var targetTop = 0
                    // Keep a small, estimated, overlap so that the last/first line is visible after scrolling
                    targetTop += resources.getDimensionPixelSize(R.dimen.fragment_webview_tap_to_scroll_offset)

                    if (appBarLayout != null) {
                        // The app bar is pushing the whole content screen down, so our visible bottom is higher up
                        visibleBottom -= appBarLayout.bottom

                        // The app bar will be hidden, so we want the scrolled content to align below the status bar
                        targetTop += getStatusBarHeight()

                        appBarLayout.setExpanded(false, true)
                    }

                    if (bottomNavigationLayout != null) {
                        if (bottomNavigationBehavior != null) {
                            // If the bottom navigation is shown, the visible content bottom is higher up
                            visibleBottom -= bottomNavigationBehavior.getVisibleHeight(bottomNavigationLayout)
                            bottomNavigationBehavior.collapse(bottomNavigationLayout, true)
                        } else {
                            // If the bottom navigation does not have a behavior, it is expanded
                            visibleBottom -= bottomNavigationLayout.height
                        }

                    }

                    val scrollDelta = visibleBottom - targetTop
                    val scrollAnimation = ObjectAnimator.ofInt(
                        webView,
                        "scrollY",
                        webView.scrollY,
                        webView.scrollY + scrollDelta
                    )
                    scrollAnimation.start()
                }

                SCROLL_BACKWARDS -> {
                    var visibleTop = 0
                    var targetBottom = webView.height
                    // Keep a small, estimated, overlap so that the last/first line is visible after scrolling
                    targetBottom -= resources.getDimensionPixelSize(R.dimen.fragment_webview_tap_to_scroll_offset)

                    if (appBarLayout != null) {
                        if (appBarLayout.bottom == 0) {
                            // If the app bar is currently hidden, we still want to ignore the content below the translucent status bar for scrolling
                            visibleTop += getStatusBarHeight()
                        }

                        // The app bar will be shown after scrolling, and pushing the content down.
                        // So our visible target bottom will be higher up
                        targetBottom -= appBarLayout.height

                        appBarLayout.setExpanded(true, true)
                    }

                    if (bottomNavigationLayout != null) {
                        // The bottom navigation will be shown after scrolling, so we have to adjust our visible target bottom
                        targetBottom -= bottomNavigationLayout.height
                        bottomNavigationBehavior?.expand(bottomNavigationLayout, true)
                    }

                    val scrollDelta = targetBottom - visibleTop
                    val scrollAnimation = ObjectAnimator.ofInt(
                        webView,
                        "scrollY",
                        webView.scrollY,
                        webView.scrollY - scrollDelta
                    )
                    scrollAnimation.start()
                }
            }
        }
    }

    private fun getStatusBarHeight(): Int {
        val rootWindowInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view?.rootWindowInsets
        } else {
            null
        }

        val statusBarHeight =
            if (rootWindowInsets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                rootWindowInsets.getInsets(WindowInsets.Type.systemBars()).top
            } else if (rootWindowInsets != null) {
                rootWindowInsets.systemWindowInsetTop
            } else {
                val legacyStatusBarHeightDp = 24f
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    legacyStatusBarHeightDp,
                    resources.displayMetrics
                ).toInt()
            }

        if (statusBarHeight <= 0 && sentryLoggedStatusBarHeight0) {
            sentryLoggedStatusBarHeight0 = true
            log.warn("Encountered status bar height of $statusBarHeight: \nRootWindowInsets: $rootWindowInsets")
            SentryWrapper.captureMessage("Encountered status bar height of 0")
        }

        return statusBarHeight
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
                    resources.getIdentifier("navigation_bar_height", "dimen", "android"                    )
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
    open suspend fun setupBookmarkHandling(articleNamesInWebView: List<String>): List<String> = emptyList()
    open suspend fun onSetBookmark(articleName: String, isBookmarked: Boolean, showNotification: Boolean) = Unit
    /**
     * Setup the handling for the playlist in the current webview.
     * It must return a list of all Article names enqueued in the initial state.
     *
     * Return a List of enqueued Article names (without the .html suffix) for the current webview.
     * The context is usually a Section - otherwise the behavior is not defined.
     */
    open suspend fun setupEnqueuedHandling(articleNamesInWebView: List<String>): List<String> = emptyList()
    open suspend fun onEnqueued(articleName: String, isEnqueued: Boolean) = Unit


    open fun onPageRendered() {
        isRendered = true
    }

    override fun onPageFinishedLoading() {
        // do nothing instead use onPageRendered
    }

    override fun onResume() {
        tapLock = false
        super.onResume()
    }

    open fun hideLoadingScreen() {
        activity?.runOnUiThread {
            loadingScreen.animate()?.alpha(0f)?.duration = LOADING_SCREEN_FADE_OUT_TIME
        }
    }

    private suspend fun loadUrl(url: String) = withContext(Dispatchers.Main) {
        webView.loadUrl(url)
    }

    override fun onDestroyView() {
        webView.destroy()
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
            } catch (e: CacheOperationFailedException) {
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
        setDisplayable(displayableKey)
    }

    fun setDisplayable(displayableKey: String) {
        currentIssueKey?.let {
            lifecycleScope.launch {
                issueViewerViewModel.setDisplayable(it, displayableKey)
            }
        }
    }

    suspend fun restoreLastScrollPosition() {
        viewModel.displayable?.let {
            val persistedScrollPosition = viewerStateRepository.get(it.key)?.scrollPosition
            viewModel.scrollPosition = persistedScrollPosition ?: viewModel.scrollPosition
        }
        viewModel.scrollPosition?.let {
            webView.scrollY = it
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

    private fun maybeScroll(@ScrollDirection direction: Int) : Boolean {
        if ((tapToScroll || multiColumnMode) && view != null) {
            if (!tapLock) {
                tapLock = true
                lifecycleScope.launch {
                    scrollToDirection(multiColumnMode, direction)
                    // wait some delay to prevent javascript form opening links
                    delay(TAP_LOCK_DELAY_MS)
                    tapLock = false
                }
            }
            return true
        }
        return false
    }

    fun addBottomMarginIfNecessary() {
        val isTablet = resources.getBoolean(R.bool.isTablet)
        if (isTablet && !multiColumnMode && webView.marginBottom == 0) {
            val heightOfToolBar = bottomNavigationLayout?.height ?: 0
            webView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = heightOfToolBar
            }
        }
    }
}