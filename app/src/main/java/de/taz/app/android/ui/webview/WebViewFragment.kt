package de.taz.app.android.ui.webview

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.widget.LinearLayout
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_TAP_TO_SCROLL_OFFSET
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.ViewerStateRepository
import de.taz.app.android.singletons.CannotDetermineBaseUrlException
import de.taz.app.android.singletons.DEFAULT_COLUMN_GAP_PX
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.TazApiCssHelper
import de.taz.app.android.ui.ViewBorder
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SAVE_SCROLL_POS_DEBOUNCE_MS = 100L
private const val TAP_LOCK_DELAY_MS = 100L

@Retention(AnnotationRetention.SOURCE)
@IntDef(SCROLL_BACKWARDS, SCROLL_FORWARD)
private annotation class ScrollDirection
private const val SCROLL_FORWARD = 1
private const val SCROLL_BACKWARDS = -1


abstract class WebViewFragment<
        DISPLAYABLE : WebViewDisplayable,
        VIEW_MODEL : WebViewViewModel<DISPLAYABLE>,
        VIEW_BINDING : ViewBinding,
        > : BaseViewModelFragment<VIEW_MODEL, VIEW_BINDING>(), AppWebViewClientCallBack {

    abstract override val viewModel: VIEW_MODEL

    protected val log by Log

    abstract val nestedScrollViewId: Int

    private lateinit var storageService: StorageService
    private lateinit var contentService: ContentService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var viewerStateRepository: ViewerStateRepository
    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var tazApiCssHelper: TazApiCssHelper

    protected var isRendered = false

    private var saveScrollPositionJob: Job? = null

    private var currentIssueKey: IssueKey? = null

    var tapLock = false

    val issueViewerViewModel: IssueViewerViewModel by activityViewModels()

    // FIXME (johannes): consider not doing a findViewById every time this property i accessed for saving some cpu cycles
    protected val webView: AppWebView
        get() = viewBinding.root.findViewById(R.id.web_view)

    abstract fun reloadAfterCssChange()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        contentService = ContentService.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
        viewerStateRepository =
            ViewerStateRepository.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        tazApiCssHelper = TazApiCssHelper.getInstance(context.applicationContext)
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

        viewModel.tazApiCssDataStore.multiColumnMode.asLiveData().observe(viewLifecycleOwner) {
            setupScrollPositionListener(it)
        }

        savedInstanceState?.apply {
            view.findViewById<AppBarLayout>(R.id.app_bar_layout)?.setExpanded(true, false)
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
                    ViewBorder.LEFT ->  maybeScroll(SCROLL_BACKWARDS)
                    ViewBorder.RIGHT -> maybeScroll(SCROLL_FORWARD)
                    else -> Unit
                }
            }
            addJavascriptInterface(TazApiJS(this@WebViewFragment), TAZ_API_JS)
            setBackgroundColor(ContextCompat.getColor(context, R.color.backgroundColor))
        }
    }


    private fun setupScrollPositionListener(isMultiColumnMode: Boolean) {
        val nestedScrollView: NestedScrollView? = view?.findViewById(nestedScrollViewId)
        if (isMultiColumnMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                webView.setOnScrollChangeListener { _: View, scrollX: Int, _: Int, _: Int, _: Int ->
                    saveScrollPositionDebounced(scrollPositionHorizontal = scrollX)
                }
            }
            // View defines the setOnScrollChangeListener with a type of View.OnScrollChangeListener
            // NestedScrollView adds its own setOnScrollChangeListener with a type of NestedScrollView.OnScrollChangeListener
            // To let the compiler know which method to call when passing null we have to set this explicit type hint
            nestedScrollView?.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)

        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                webView.setOnScrollChangeListener(null)
            }
            nestedScrollView?.setOnScrollChangeListener { _: NestedScrollView, _: Int, scrollY: Int, _: Int, _: Int ->
                saveScrollPositionDebounced(scrollPosition = scrollY)
            }
        }
    }

    private fun saveScrollPositionDebounced(scrollPosition: Int = 0, scrollPositionHorizontal: Int = 0) {
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

    private fun scrollToDirection(horizontal: Boolean, @ScrollDirection direction: Int) {
        if (horizontal) scrollHorizontally(direction)
        else scrollVertically(direction)
    }

    /**
     * scroll article horizontally.
     * If at the top or the end - go to previous or next article
     */
    private fun scrollHorizontally(@ScrollDirection direction: Int) {
        val scrollWidth = view?.width ?: 0
        val scrollBy =
            (direction * (scrollWidth - DEFAULT_COLUMN_GAP_PX * resources.displayMetrics.density)).toInt()
        val currentWebView = webView
        if (currentWebView.canScrollHorizontally(direction)) {
            val amountBeingScrolled = calculateSwipeOffset()
            val scrollAnimation = ObjectAnimator.ofInt(
                currentWebView,
                "scrollX",
                currentWebView.scrollX,
                currentWebView.scrollX + scrollBy - amountBeingScrolled
            )
            scrollAnimation.start()
        } else {
            when (direction) {
                SCROLL_BACKWARDS -> issueViewerViewModel.goPreviousArticle.postValue(true)
                SCROLL_FORWARD -> issueViewerViewModel.goNextArticle.postValue(true)
            }
        }
        // When scrolling hide the tap icons
        currentWebView.showTapIconsListener?.invoke(false)
    }

    /**
     * Returns the amount of being wiped from the last column.
     */
    private fun calculateSwipeOffset(): Int {
        val scrollWidth =
            view?.width?.minus(DEFAULT_COLUMN_GAP_PX * resources.displayMetrics.density)?.toInt() ?: 0
        return webView.scrollX.mod(scrollWidth)
    }

    /**
     * scroll article into [direction]. If at the top or the end - go to previous or next article
     */
    private fun scrollVertically(@ScrollDirection direction: Int) {
        val scrollByValue = view?.height?.minus(WEBVIEW_TAP_TO_SCROLL_OFFSET) ?: 0
        val scrollHeight = direction * scrollByValue
        view?.let {
            val scrollView = it.findViewById<NestedScrollView>(nestedScrollViewId)
            // if on bottom and tap on right side go to next article
            if (!scrollView.canScrollVertically(SCROLL_FORWARD) && direction == SCROLL_FORWARD) {
                issueViewerViewModel.goNextArticle.postValue(true)
            }
            // if on bottom and tap on right side go to previous article
            else if (!scrollView.canScrollVertically(SCROLL_BACKWARDS) && direction == SCROLL_BACKWARDS) {
                issueViewerViewModel.goPreviousArticle.postValue(true)
            } else {
                lifecycleScope.launch {
                    val navBarHeight = parentFragment?.view?.findViewById<LinearLayout>(R.id.navigation_bottom_layout)
                        ?.let {
                            if (it.visibility == View.VISIBLE) {
                                it.height - it.translationY.toInt()
                            } else {
                                0
                            }
                        } ?: 0
                    var offset = navBarHeight

                    val appBarLayout = it.findViewById<AppBarLayout>(R.id.app_bar_layout)
                    val isExpanded = appBarLayout?.height?.minus(appBarLayout.bottom) == 0
                    if (scrollHeight > 0) {
                        // hide app bar bar when scrolling down
                        if (isExpanded) {
                            offset += appBarLayout.height
                            appBarLayout?.setExpanded(false, true)
                        }
                    } else {
                        appBarLayout?.setExpanded(true, true)
                    }
                    scrollView.smoothScrollBy(0, scrollHeight - offset)
                }
            }
        }
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
            view?.findViewById<View>(R.id.loading_screen)?.animate()?.alpha(0f)?.duration =
                LOADING_SCREEN_FADE_OUT_TIME
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
                Sentry.captureException(e)
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

    /**
     * This function checks if there is enough room that allow the tool bar to collapse.
     * Otherwise it is not possible to scroll down to the bottom
     */
    protected fun addPaddingBottomIfNecessary() {
        val webViewHeight = webView.height
        val screenHeight = this.resources.displayMetrics.heightPixels
        val difference = webViewHeight - screenHeight
        val navBottomHeight = this.resources.getDimensionPixelSize(R.dimen.nav_bottom_height)
        val collapsingToolBarHeight = this.resources.getDimensionPixelSize(R.dimen.fragment_header_default_height)
        val spaceNeededThatTheToolBarCanCollapse = navBottomHeight + 0.5*collapsingToolBarHeight

        if (difference < spaceNeededThatTheToolBarCanCollapse) {
            val scrollView = view?.findViewById<NestedScrollView>(nestedScrollViewId)
            log.debug("Add paddingBottom to allow the tool bar to collapse")

            val readOnContainerView = view?.findViewById<FragmentContainerView>(R.id.fragment_article_bottom_fragment_placeholder)
            if (readOnContainerView?.isVisible == true) {
                readOnContainerView.updatePadding(
                    0,0,0, spaceNeededThatTheToolBarCanCollapse.toInt()
                )
            } else {
                scrollView?.updatePadding(
                    0, 0, 0, spaceNeededThatTheToolBarCanCollapse.toInt()
                )
            }

        }
    }

    suspend fun restoreLastScrollPosition() {
        val scrollView = view?.findViewById<NestedScrollView>(nestedScrollViewId)
        viewModel.displayable?.let {
            val persistedScrollPosition = viewerStateRepository.get(it.key)?.scrollPosition
            viewModel.scrollPosition = persistedScrollPosition ?: viewModel.scrollPosition
        }
        viewModel.scrollPosition?.let {
            scrollView?.scrollY = it
        } ?: run {
            view?.findViewById<AppBarLayout>(R.id.app_bar_layout)
                ?.setExpanded(true, false)
        }
    }

    suspend fun restoreLastHorizontalScrollPosition() {
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
    }

    private fun maybeScroll(@ScrollDirection direction: Int) {
        lifecycleScope.launch {
            val tapToScroll = viewModel.tazApiCssDataStore.tapToScroll.get()
            val multiColumnMode = viewModel.tazApiCssDataStore.multiColumnMode.get()
            if (tapToScroll || multiColumnMode) {
                if (!tapLock) {
                    tapLock = true
                    scrollToDirection(multiColumnMode, direction)
                    // wait some delay to prevent javascript form opening links
                    delay(TAP_LOCK_DELAY_MS)
                    tapLock = false
                }
            }
        }
    }
}
