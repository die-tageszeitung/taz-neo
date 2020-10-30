package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import androidx.annotation.LayoutRes
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.data.DataService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.getColorFromAttr
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.singletons.SETTINGS_TEXT_NIGHT_MODE
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_section.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.*

abstract class WebViewFragment<DISPLAYABLE : WebViewDisplayable, VIEW_MODEL : WebViewViewModel<DISPLAYABLE>>(
    @LayoutRes layoutResourceId: Int
) : BaseViewModelFragment<VIEW_MODEL>(layoutResourceId), AppWebViewCallback,
    AppWebViewClientCallBack {

    override val enableSideBar: Boolean = true

    abstract override val viewModel: VIEW_MODEL

    protected val log by Log

    abstract val nestedScrollViewId: Int
    private lateinit var tazApiCssPreferences: SharedPreferences

    private lateinit var apiService: ApiService
    private lateinit var downloadService: DownloadService
    private lateinit var dataService: DataService

    private var isRendered = false

    private val tazApiCssPrefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            log.debug("WebViewFragment: shared pref changed: $key")
            web_view?.injectCss(sharedPreferences)
            if (
                key == SETTINGS_TEXT_NIGHT_MODE &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.N
            ) {
                web_view?.reload()
            }
        }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        downloadService = DownloadService.getInstance(context.applicationContext)
        dataService = DataService.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getMainActivity()?.applicationContext?.let { applicationContext ->
            tazApiCssPreferences =
                applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)
            tazApiCssPreferences.registerOnSharedPreferenceChangeListener(tazApiCssPrefListener)
        }
        viewModel.displayableLiveData.observeDistinct(this) { displayable ->
            if (displayable == null) return@observeDistinct
            setHeader(displayable)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.displayableLiveData.observeDistinct(this.viewLifecycleOwner) {
            if (it == null) return@observeDistinct
            log.debug("Received a new displayable ${it.key}")
            lifecycleScope.launch(Dispatchers.Main) {
                configureWebView()
                ensureDownloadedAndShow()
            }
        }

        view.findViewById<NestedScrollView>(nestedScrollViewId).apply {
            setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
                viewModel.scrollPosition = scrollY
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
            webViewClient = AppWebViewClient(this@WebViewFragment)
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
        val nestedScrollView = view?.findViewById<NestedScrollView>(nestedScrollViewId)
        viewModel.scrollPosition?.let {
            nestedScrollView?.scrollY = it
        } ?: view?.findViewById<AppBarLayout>(R.id.app_bar_layout)?.setExpanded(true, false)
        hideLoadingScreen()
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

    fun getMainActivity(): MainActivity? {
        return activity as? MainActivity
    }

    override fun onDestroy() {
        super.onDestroy()
        web_view?.destroy()
        tazApiCssPreferences.unregisterOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }

    private suspend fun ensureDownloadedAndShow() {
        var resourceInfo = dataService.getResourceInfo()
        if (!isResourceInfoUpToDate(resourceInfo)) {
            resourceInfo = dataService.getResourceInfo(allowCache = false)
        }

        dataService.ensureDownloaded(resourceInfo)

        log.info("displayable is ready")
        viewModel.displayable?.let {
            dataService.ensureDownloaded(it)
            val path = withContext(Dispatchers.IO) { it.getFile()!!.absolutePath }
            loadUrl("file://$path")
        }
    }

    override fun onLinkClicked(displayableKey: String) {
        getMainActivity()?.showInWebView(displayableKey)
    }

    /**
     * Check if minimal resource version of the issue is <= the current resource version.
     * @return Boolean if resource info is up to date or not
     */
    private suspend fun isResourceInfoUpToDate(resourceInfo: ResourceInfo?): Boolean =
        withContext(Dispatchers.IO) {
            val issue = viewModel.displayable?.getIssueStub()
            resourceInfo?.let {
                val minResourceVersion = issue?.minResourceVersion ?: Int.MAX_VALUE
                minResourceVersion <= resourceInfo.resourceVersion
            } ?: true
        }

}
