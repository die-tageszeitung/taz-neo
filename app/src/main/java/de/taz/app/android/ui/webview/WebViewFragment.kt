package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import androidx.annotation.LayoutRes
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.base.ViewModelBaseMainFragment
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.observeDistinctOnce
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.singletons.SETTINGS_TEXT_FONT_SIZE
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_section.web_view
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


abstract class WebViewFragment<DISPLAYABLE : WebViewDisplayable>(
    @LayoutRes layoutResourceId: Int
) : ViewModelBaseMainFragment(layoutResourceId), AppWebViewCallback, AppWebViewClientCallBack {

    private val log by Log

    abstract val viewModel: WebViewViewModel<DISPLAYABLE>

    private lateinit var tazApiCssPreferences: SharedPreferences

    private val tazApiCssPrefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            log.debug("WebViewFragment: shared pref changed: $key")
            if (key == SETTINGS_TEXT_FONT_SIZE) {
                web_view?.injectCss(sharedPreferences)
            }
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
        configureWebView()
        viewModel.displayableLiveData.observeDistinctOnce(this) { displayable ->
            displayable?.let {
                setHeader(displayable)
                viewModel.displayable?.let {
                    lifecycleScope.launch(Dispatchers.IO) {
                        ensureDownloadedAndShow(displayable)
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun configureWebView() {
        web_view?.apply {
            webViewClient = AppWebViewClient(this@WebViewFragment)
            webChromeClient = WebChromeClient()
            settings.javaScriptEnabled = true
            addJavascriptInterface(TazApiJS(this@WebViewFragment), TAZ_API_JS)
        }
    }

    abstract fun setHeader(displayable: DISPLAYABLE)

    open fun hideLoadingScreen() {
        activity?.runOnUiThread {
            loading_screen?.animate()?.setDuration(300)?.alpha(0f)?.start()
        }
    }

    fun loadUrl(url: String) {
        activity?.runOnUiThread {
            web_view.loadUrl(url)
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

    // TODO REFACTOR
    private suspend fun ensureDownloadedAndShow(displayable: DISPLAYABLE) {
        val isDisplayableLiveData = MediatorLiveData<Boolean>()

        val isResourceInfoUpToDate = isResourceInfoUpToDate()

        val resourceInfo = if (isResourceInfoUpToDate) {
            ResourceInfoRepository.getInstance().get()
        } else {
            tryGetResourceInfo()
        }

        resourceInfo?.let {
            val isDownloadingLiveData = displayable.isDownloadedOrDownloadingLiveData()
            val isDownloadedLiveData = displayable.isDownloadedLiveData()

            withContext(Dispatchers.Main) {
                isDownloadingLiveData.observe(
                    this@WebViewFragment,
                    Observer { isDownloadedOrDownloading ->
                        if (!isDownloadedOrDownloading) {
                            runBlocking(Dispatchers.IO) {
                                DownloadService.getInstance().download(displayable)
                            }
                        }
                    }
                )
                isDownloadedLiveData.observe(
                    this@WebViewFragment,
                    Observer { isDownloaded ->
                        if (isDownloaded) {
                            isDisplayableLiveData.removeSource(displayable.isDownloadedLiveData())
                            runBlocking(Dispatchers.IO) {
                                isDisplayableLiveData.postValue(resourceInfo.isDownloaded())
                            }
                        }
                    }
                )

                val resourceInfoIsDownloadingLiveData =
                    resourceInfo.isDownloadedOrDownloadingLiveData()
                val resourceInfoIsDownloadedLiveData =
                    resourceInfo.isDownloadedLiveData()

                withContext(Dispatchers.Main) {
                    if (!isResourceInfoUpToDate) {
                        resourceInfoIsDownloadingLiveData.observe(
                            this@WebViewFragment,
                            Observer { isDownloadedOrDownloading ->
                                if (!isDownloadedOrDownloading) {
                                    launch(Dispatchers.IO) {
                                        DownloadService.getInstance().download(resourceInfo)
                                    }
                                }
                            }
                        )
                    }
                    resourceInfoIsDownloadedLiveData.observe(
                        this@WebViewFragment,
                        Observer { isDownloaded ->
                            if (isDownloaded) {
                                isDisplayableLiveData.removeSource(resourceInfo.isDownloadedLiveData())
                                launch(Dispatchers.IO) {
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
                    Observer { isDisplayable ->
                        if (isDisplayable) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                displayable.getFile()?.let { file ->
                                    log.debug("file ${file.absolutePath} exists: ${file.exists()}")
                                    loadUrl("file://${file.absolutePath}")
                                }
                            }
                        }
                    }
                )
            }
        }
    }


    override fun onLinkClicked(displayable: WebViewDisplayable) {
        getMainActivity()?.showInWebView(displayable)
    }

    override fun onPageFinishedLoading() {
        hideLoadingScreen()
    }

    /**
     * Try to get the resourceInfo from apiService.
     * @return ResourceInfo or null
     */
    private suspend fun tryGetResourceInfo(): ResourceInfo? {
        return try {
            ApiService.getInstance().getResourceInfo()?.let {
                ResourceInfoRepository.getInstance().save(it)
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

        val issue = IssueRepository.getInstance().getIssueByFeedAndDate(
            issueOperations?.feedName ?: "",
            issueOperations?.date ?: "",
            issueOperations?.status ?: IssueStatus.public
        )

        val minResourceVersion = issue?.minResourceVersion ?: 0
        val currentResourceVersion =
            ResourceInfoRepository.getInstance().get()?.resourceVersion ?: 0

        return minResourceVersion <= currentResourceVersion
    }

}
