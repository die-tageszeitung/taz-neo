package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.widget.LinearLayout
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import androidx.viewbinding.ViewBinding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_TAP_TO_SCROLL_OFFSET
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.monkey.getColorFromAttr
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.ViewerStateRepository
import de.taz.app.android.singletons.CannotDetermineBaseUrlException
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.ViewBorder
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.*

const val SAVE_SCROLL_POS_DEBOUNCE_MS = 100L

abstract class WebViewFragment<
        DISPLAYABLE : WebViewDisplayable,
        VIEW_MODEL : WebViewViewModel<DISPLAYABLE>,
        VIEW_BINDING : ViewBinding
        > : BaseViewModelFragment<VIEW_MODEL, VIEW_BINDING>(), AppWebViewCallback,
    AppWebViewClientCallBack {

    abstract override val viewModel: VIEW_MODEL

    protected val log by Log

    abstract val nestedScrollViewId: Int

    private lateinit var apiService: ApiService
    private lateinit var storageService: StorageService
    private lateinit var contentService: ContentService
    private lateinit var toastHelper: ToastHelper
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var viewerStateRepository: ViewerStateRepository
    private lateinit var generalDataStore: GeneralDataStore

    private var isRendered = false

    private var saveScrollPositionJob: Job? = null

    private var currentIssueKey: IssueKey? = null

    val issueViewerViewModel: IssueViewerViewModel by activityViewModels()

    protected val webView: AppWebView
        get() = viewBinding.root.findViewById(R.id.web_view)

    private fun reloadAfterCssChange() {
        lifecycleScope.launch {
            whenCreated {
                webView.injectCss()
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N)
                    webView.reload()
            }
        }
    }

    private fun saveScrollPositionDebounced(scrollPosition: Int) {
        viewModel.displayable?.let {
            val oldJob = saveScrollPositionJob
            saveScrollPositionJob = lifecycleScope.launch {
                oldJob?.cancelAndJoin()
                delay(SAVE_SCROLL_POS_DEBOUNCE_MS)
                val bottomNavigationViewLayout = try {
                    // we need to offset the scroll position by the bottom navigation as it might be expanded
                    withContext(Dispatchers.Main) {
                        parentFragment?.view?.findViewById<LinearLayout>(R.id.navigation_bottom_layout)
                    }
                } catch (e: NullPointerException) {
                    // view might be cleaned up in meantime,
                    // then skip the exception and don't do anything
                    return@launch
                }
                val bottomOffset = bottomNavigationViewLayout?.let {
                    if (it.visibility == View.VISIBLE) {
                        it.height - it.translationY.toInt()
                    } else {
                        0
                    }
                } ?: 0

                val offsetPosition = scrollPosition - bottomOffset
                viewModel.scrollPosition = offsetPosition
                viewerStateRepository.save(
                    it.key,
                    offsetPosition
                )
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        contentService = ContentService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
        storageService = StorageService.getInstance(requireContext().applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(requireContext().applicationContext)
        viewerStateRepository =
            ViewerStateRepository.getInstance(requireContext().applicationContext)
        generalDataStore = GeneralDataStore.getInstance(requireContext().applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.displayableLiveData.observeDistinct(this) { displayable ->
            if (displayable == null) return@observeDistinct
            setHeader(displayable)
        }
        viewModel.nightModeLiveData.observe(this@WebViewFragment) {
            reloadAfterCssChange()
        }
        viewModel.fontSizeLiveData.observe(this@WebViewFragment) {
            reloadAfterCssChange()
        }
        viewModel.scrollBy.observe(this@WebViewFragment) {
            lifecycleScope.launch {
                val tapToScroll = viewModel.tazApiCssDataStore.tapToScroll.get()

                if (tapToScroll) {
                    // wait if javascript interface did some interactions (and set the lock)
                    delay(SAVE_SCROLL_POS_DEBOUNCE_MS)
                    if (viewModel.tapLock.value == false) {
                        scrollBy(it)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.displayableLiveData.observeDistinct(this.viewLifecycleOwner) {
            if (it == null) return@observeDistinct
            log.debug("Received a new displayable ${it.key}")
            lifecycleScope.launch {
                currentIssueKey = it.getIssueStub(requireContext().applicationContext)?.issueKey
                configureWebView()
                ensureDownloadedAndShow()
            }
        }

        view.findViewById<NestedScrollView>(nestedScrollViewId).apply {
            setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
                saveScrollPositionDebounced(scrollY)
            }
        }

        savedInstanceState?.apply {
            view.findViewById<AppBarLayout>(R.id.app_bar_layout)?.setExpanded(true, false)
        }

        // Adjust padding when we have cutout display
        lifecycleScope.launch {
            val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
            if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar_layout)
                    ?.setPadding(0, extraPadding, 0, 0)
            }
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
                val scrollByValue = view?.height?.minus(WEBVIEW_TAP_TO_SCROLL_OFFSET) ?: 0
                when (border) {
                    ViewBorder.LEFT -> viewModel.scrollBy.value = -scrollByValue
                    ViewBorder.RIGHT -> viewModel.scrollBy.value = scrollByValue
                    else -> {}
                }
            }
            addJavascriptInterface(TazApiJS(this@WebViewFragment), TAZ_API_JS)
            setBackgroundColor(context.getColorFromAttr(R.color.backgroundColor))
        }
    }

    /**
     * scroll article by [scrollHeight]. If at the top or the end - go to previous or next article
     */
    private fun scrollBy(scrollHeight: Int) {
        view?.let {
            val scrollView = it.findViewById<NestedScrollView>(nestedScrollViewId)
            // if on bottom and tap on right side go to next article
            if (!scrollView.canScrollVertically(1) && scrollHeight > 0) {
                issueViewerViewModel.goNextArticle.postValue(true)
            }
            // if on bottom and tap on right side go to next article
            else if (!scrollView.canScrollVertically(-1) && scrollHeight < 0) {
                issueViewerViewModel.goPreviousArticle.postValue(true)
            } else {
                lifecycleScope.launch(Dispatchers.Main) {
                    // hide app bar bar when scrolling down
                    if (scrollHeight > 0) {
                        it.findViewById<AppBarLayout>(R.id.app_bar_layout)?.setExpanded(false, true)
                    } else {
                        it.findViewById<AppBarLayout>(R.id.app_bar_layout)?.setExpanded(true, true)
                    }
                }
                scrollView.smoothScrollBy(0, scrollHeight)
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
        val scrollView = view?.findViewById<NestedScrollView>(nestedScrollViewId)
        lifecycleScope.launch {
            viewModel.displayable?.let {
                val persistedScrollPosition = viewerStateRepository.get(it.key)?.scrollPosition
                viewModel.scrollPosition = persistedScrollPosition ?: viewModel.scrollPosition
            }
            withContext(Dispatchers.Main) {
                viewModel.scrollPosition?.let {
                    scrollView?.scrollY = it
                } ?: run {
                    view?.findViewById<AppBarLayout>(R.id.app_bar_layout)
                        ?.setExpanded(true, false)
                }
                hideLoadingScreen()
                addPaddingBottomIfNecessary()
            }
        }
    }

    override fun onPageFinishedLoading() {
        // do nothing instead use onPageRendered
    }

    override fun onResume() {
        viewModel.tapLock.value = false
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
                val hint = "Could not determine baseurl for the displayable ${displayable.key}"
                log.error(hint, e)
                Sentry.captureException(e, hint)
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
    private fun addPaddingBottomIfNecessary() {
        val webViewHeight = webView.height
        val screenHeight = this.resources.displayMetrics.heightPixels
        val difference = webViewHeight - screenHeight
        val navBottomHeight = this.resources.getDimensionPixelSize(R.dimen.nav_bottom_height)
        val collapsingToolBarHeight = this.resources.getDimensionPixelSize(R.dimen.fragment_header_default_height)
        val spaceNeededThatTheToolBarCanCollapse = navBottomHeight + 0.5*collapsingToolBarHeight

        if (difference < spaceNeededThatTheToolBarCanCollapse) {
            val scrollView = view?.findViewById<NestedScrollView>(nestedScrollViewId)
            log.debug("Add paddingBottom to allow the tool bar to collapse")
            scrollView?.updatePadding(
                0,0,0, spaceNeededThatTheToolBarCanCollapse.toInt()
            )
        }
    }
}
