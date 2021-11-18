package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.data.DataService
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.monkey.getColorFromAttr
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.util.showConnectionErrorDialog
import kotlinx.android.synthetic.main.fragment_webview_section.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.*

const val SAVE_SCROLL_POS_DEBOUNCE_MS = 100L

abstract class WebViewFragment<DISPLAYABLE : WebViewDisplayable, VIEW_MODEL : WebViewViewModel<DISPLAYABLE>>(
    @LayoutRes layoutResourceId: Int
) : BaseViewModelFragment<VIEW_MODEL>(layoutResourceId), AppWebViewCallback,
    AppWebViewClientCallBack {

    abstract override val viewModel: VIEW_MODEL

    protected val log by Log

    abstract val nestedScrollViewId: Int

    private lateinit var apiService: ApiService
    private lateinit var storageService: StorageService
    private lateinit var dataService: DataService
    private lateinit var contentService: ContentService
    private lateinit var toastHelper: ToastHelper
    private lateinit var fileEntryRepository: FileEntryRepository

    private var isRendered = false

    private var saveScrollPositionJob: Job? = null

    private var currentIssueKey: IssueKey? = null

    val issueViewerViewModel: IssueViewerViewModel by activityViewModels()

    private fun reloadAfterCssChange() {
        CoroutineScope(Dispatchers.Main).launch {
            web_view?.injectCss()
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N)
                web_view?.reload()
        }
    }

    private fun saveScrollPositionDebounced(scrollPosition: Int) {
        viewModel.displayable?.let {
            val oldJob = saveScrollPositionJob
            saveScrollPositionJob = lifecycleScope.launch(Dispatchers.IO) {
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
                dataService.saveViewerStateForDisplayable(
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
        dataService = DataService.getInstance(requireContext().applicationContext)
        toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
        storageService = StorageService.getInstance(requireContext().applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(requireContext().applicationContext)
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
        viewModel.fontSizeLiveData.observe(this@WebViewFragment){
            reloadAfterCssChange()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.displayableLiveData.observeDistinct(this.viewLifecycleOwner) {
            if (it == null) return@observeDistinct
            log.debug("Received a new displayable ${it.key}")
            lifecycleScope.launch(Dispatchers.Main) {
                withContext(Dispatchers.IO) {
                    currentIssueKey = it.getIssueStub(requireContext().applicationContext)?.issueKey
                }
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
        view.findViewById<NestedScrollView>(nestedScrollViewId)
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun configureWebView() {
        web_view?.apply {
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
                setAppCacheEnabled(false)
            }
            addJavascriptInterface(TazApiJS(this@WebViewFragment), TAZ_API_JS)
            setBackgroundColor(context.getColorFromAttr(R.color.backgroundColor))
        }
    }

    abstract fun setHeader(displayable: DISPLAYABLE)

    open fun onPageRendered() {
        isRendered = true
        val scrollView = view?.findViewById<NestedScrollView>(nestedScrollViewId)
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.displayable?.let {
                val persistedScrollPosition =
                    dataService.getViewerStateForDisplayable(it.key)?.scrollPosition
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
            }
        }
    }

    override fun onPageFinishedLoading() {
        // do nothing instead use onPageRendered
    }

    open fun hideLoadingScreen() {
        activity?.runOnUiThread {
            loading_screen?.animate()?.alpha(0f)?.duration = LOADING_SCREEN_FADE_OUT_TIME
        }
    }

    private fun loadUrl(url: String) {
        activity?.runOnUiThread {
            web_view?.loadUrl(url)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        web_view?.destroy()
    }

    private suspend fun ensureDownloadedAndShow() = withContext(Dispatchers.Main) {
        viewModel.displayable?.let { displayable ->
            log.info("Displayable is $displayable")
            try {
                contentService.downloadToCache(displayable, priority = DownloadPriority.High)
                val displayableFile = withContext(Dispatchers.IO) {
                    fileEntryRepository.get(displayable.key)
                }
                val path = displayableFile?.let {
                    storageService.getFileUri(it)
                }
                path?.let { loadUrl(it) }
            } catch (e: CacheOperationFailedException) {
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
     * Check if minimal resource version of the issue is <= the current resource version.
     * @return Boolean if resource info is up to date or not
     */
    private suspend fun isResourceInfoUpToDate(resourceInfo: ResourceInfo?): Boolean =
        withContext(Dispatchers.IO) {
            val issue = viewModel.displayable?.getIssueStub(requireContext().applicationContext)
            resourceInfo?.let {
                val minResourceVersion = issue?.minResourceVersion ?: Int.MAX_VALUE
                minResourceVersion <= resourceInfo.resourceVersion
            } ?: true
        }
}
