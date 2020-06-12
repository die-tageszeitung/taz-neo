package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.singletons.SETTINGS_TEXT_FONT_SIZE
import de.taz.app.android.singletons.SETTINGS_TEXT_NIGHT_MODE
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_section.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SCROLL_POSITION = "scrollPosition"

abstract class WebViewFragment<DISPLAYABLE : WebViewDisplayable, VIEW_MODEL : WebViewViewModel<DISPLAYABLE>>(
    @LayoutRes layoutResourceId: Int
) : BaseViewModelFragment<VIEW_MODEL>(layoutResourceId), AppWebViewCallback,
    AppWebViewClientCallBack {

    protected var displayable: DISPLAYABLE? = null
    abstract override val viewModel: VIEW_MODEL

    protected val log by Log

    abstract val nestedScrollViewId: Int
    private lateinit var tazApiCssPreferences: SharedPreferences

    private var apiService: ApiService? = null
    private var downloadService: DownloadService? = null
    private var resourceInfoRepository: ResourceInfoRepository? = null

    private val tazApiCssPrefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            log.debug("WebViewFragment: shared pref changed: $key")
            if (key == SETTINGS_TEXT_FONT_SIZE) {
                web_view?.injectCss(sharedPreferences)
            } else if (
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
        resourceInfoRepository = ResourceInfoRepository.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getMainActivity()?.applicationContext?.let { applicationContext ->
            tazApiCssPreferences =
                applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)
            tazApiCssPreferences.registerOnSharedPreferenceChangeListener(tazApiCssPrefListener)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (viewModel.displayable == null) {
            viewModel.displayable = displayable
        }

        configureWebView()
        viewModel.displayable?.let { displayable ->
            lifecycleScope.launch(Dispatchers.IO) {
                ensureDownloadedAndShow(displayable)
            }
        }

        view.findViewById<NestedScrollView>(nestedScrollViewId)
            .setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
                viewModel.scrollPosition = scrollY
            }

        savedInstanceState?.apply {
            viewModel.scrollPosition = getInt(SCROLL_POSITION)
            view.findViewById<AppBarLayout>(R.id.app_bar_layout)?.setExpanded(true, false)
        }

    }

    override fun onStart() {
        super.onStart()
        viewModel.displayable?.let { displayable ->
            setHeader(displayable)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun configureWebView() {
        web_view?.apply {
            webViewClient = AppWebViewClient(this@WebViewFragment)
            webChromeClient = AppWebChromeClient(::onPageRendered)
            settings.javaScriptEnabled = true
            addJavascriptInterface(TazApiJS(this@WebViewFragment), TAZ_API_JS)
        }
    }

    abstract fun setHeader(displayable: DISPLAYABLE)

    private fun onPageRendered() {
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

    fun showFontSettingBottomSheet() {
        showBottomSheet(TextSettingsFragment())
    }

    override fun onDestroy() {
        super.onDestroy()
        web_view?.destroy()
        tazApiCssPreferences.unregisterOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }

    private suspend fun ensureDownloadedAndShow(displayable: DISPLAYABLE) {
        val isDisplayableLiveData = MutableLiveData<Boolean>()

        // Ensure only one resourceVersion exists by deleting all but newest:
        ResourceInfoRepository.getInstance(activity?.applicationContext).deleteAllButNewest()

        val isResourceInfoUpToDate = isResourceInfoUpToDate()

        val resourceInfo = if (isResourceInfoUpToDate) {
            resourceInfoRepository?.get()
        } else {
            tryGetResourceInfo()
        }

        resourceInfo?.let {
            val isDownloadedLiveData = displayable.isDownloadedLiveData()

            withContext(Dispatchers.Main) {
                isDownloadedLiveData.observeDistinct(
                    this@WebViewFragment,
                    Observer { isDownloaded ->
                        if (isDownloaded) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                log.info("displayable is ready")
                                isDisplayableLiveData.postValue(resourceInfo.isDownloaded())
                            }
                        } else {
                            downloadService?.download(displayable)
                        }
                    }
                )

                val resourceInfoIsDownloadedLiveData =
                    resourceInfo.isDownloadedLiveData()

                withContext(Dispatchers.Main) {
                    if (!isResourceInfoUpToDate) {
                        ResourceInfo.update()
                    }
                    resourceInfoIsDownloadedLiveData.observeDistinct(
                        this@WebViewFragment,
                        Observer { isDownloaded ->
                            if (isDownloaded) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    log.info("resources are ready")
                                    isDisplayableLiveData.postValue(displayable.isDownloaded())
                                }
                            }
                        }
                    )
                }
            }


            withContext(Dispatchers.Main) {
                isDisplayableLiveData.observe(
                    this@WebViewFragment,
                    DisplayableObserver(displayable, isDisplayableLiveData)
                )
            }
        }
    }

    inner class DisplayableObserver(
        private val displayable: DISPLAYABLE,
        private val isDisplayableLiveData: LiveData<Boolean>
    ) : Observer<Boolean> {
        override fun onChanged(isDisplayable: Boolean?) {
            if (isDisplayable == true) {
                isDisplayableLiveData.removeObserver(this@DisplayableObserver)
                lifecycleScope.launch(Dispatchers.IO) {
                    displayable.getFile()?.let { file ->
                        log.debug("file ${file.absolutePath} exists: ${file.exists()}")
                        loadUrl("file://${file.absolutePath}")
                    }
                }
            }
        }
    }

    override fun onLinkClicked(displayableKey: String) {
        getMainActivity()?.showInWebView(displayableKey)
    }

    /**
     * Try to get the resourceInfo from apiService.
     * @return ResourceInfo or null
     */
    private suspend fun tryGetResourceInfo(): ResourceInfo? {
        return try {
            apiService?.getResourceInfo()?.let {
                resourceInfoRepository?.save(it)
                it
            } ?: run {
                getMainActivity()?.showToast(R.string.something_went_wrong_try_later)
                null
            }
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            getMainActivity()?.showToast(R.string.toast_no_internet)
            null
        }
    }

    /**
     * Check if minimal resource version of the issue is <= the current resource version.
     * @return Boolean if resource info is up to date or not
     */
    private fun isResourceInfoUpToDate(): Boolean {
        val issueOperations = viewModel.displayable?.getIssueOperations()
        val minResourceVersion = issueOperations?.minResourceVersion ?: 0
        val currentResourceVersion = resourceInfoRepository?.get()?.resourceVersion ?: 0

        return minResourceVersion <= currentResourceVersion
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.scrollPosition?.let {
            outState.putInt(SCROLL_POSITION, it)
        }
        super.onSaveInstanceState(outState)
    }

}
