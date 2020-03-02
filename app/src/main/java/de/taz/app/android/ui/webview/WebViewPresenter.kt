package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.WebChromeClient
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import android.util.Base64
import androidx.lifecycle.MediatorLiveData
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.util.Log
import de.taz.app.android.singletons.TazApiCssHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


const val TAZ_API_JS = "ANDROIDAPI"

abstract class WebViewPresenter<DISPLAYABLE : WebViewDisplayable>(
    private val apiService: ApiService = ApiService.getInstance(),
    private val resourceInfoRepository: ResourceInfoRepository = ResourceInfoRepository.getInstance()
) : BasePresenter<WebViewContract.View<DISPLAYABLE>, WebViewDataController<DISPLAYABLE>>(
    WebViewDataController<DISPLAYABLE>().javaClass
), WebViewContract.Presenter {

    private val log by Log

    override fun attach(view: WebViewContract.View<DISPLAYABLE>) {

        super.attach(view)
        view.getWebViewDisplayable()?.let {
            viewModel?.setWebViewDisplayable(it)
        } ?: view.setWebViewDisplayable(viewModel?.getWebViewDisplayable())
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        configureWebView()
        observeFile()
    }


    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun configureWebView() {
        getView()?.let { webContractView ->
            val tazApiJS = TazApiJS(webContractView)
            webContractView.getWebView()?.apply {
                webViewClient = AppWebViewClient(this@WebViewPresenter)
                webChromeClient = WebChromeClient()
                settings.javaScriptEnabled = true
                addJavascriptInterface(tazApiJS, TAZ_API_JS)
                setArticleWebViewCallback(this@WebViewPresenter)
            }
        }
    }

    private fun observeFile() {
        getView()?.apply {
            viewModel?.observeWebViewDisplayable(getLifecycleOwner()) { displayable ->
                displayable?.let {
                    getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                        ensureDownloadedAndShow(displayable)
                    }
                }
            }
        }
    }

    private suspend fun ensureDownloadedAndShow(displayable: DISPLAYABLE) {
        val isDisplayableLiveData = MediatorLiveData<Boolean>()

        val isResourceInfoUpToDate = isResourceInfoUpToDate()

        val resourceInfo = if (isResourceInfoUpToDate) {
            resourceInfoRepository.get()
        } else {
            tryGetResourceInfo()
        }

        resourceInfo?.let {
            getView()?.let { view ->
                view.getMainView()?.getApplicationContext()?.let {

                    val isDownloadingLiveData = displayable.isDownloadedOrDownloadingLiveData()
                    val isDownloadedLiveData = displayable.isDownloadedLiveData()
                    withContext(Dispatchers.Main) {
                        isDownloadingLiveData.observe(
                            view.getLifecycleOwner(),
                            Observer { isDownloadedOrDownloading ->
                                if (!isDownloadedOrDownloading) {
                                    runBlocking(Dispatchers.IO) {
                                        DownloadService.download(it, displayable)
                                    }
                                }
                            }
                        )
                        isDownloadedLiveData.observe(
                            view.getLifecycleOwner(),
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
                                    view.getLifecycleOwner(),
                                    Observer { isDownloadedOrDownloading ->
                                        if (!isDownloadedOrDownloading) {
                                            DownloadService.download(it, resourceInfo)
                                        }
                                    }
                                )
                            }
                            resourceInfoIsDownloadedLiveData.observe(
                                view.getLifecycleOwner(),
                                Observer { isDownloaded ->
                                    if (isDownloaded) {
                                        isDisplayableLiveData.removeSource(resourceInfo.isDownloadedLiveData())
                                        runBlocking(Dispatchers.IO) {
                                            isDisplayableLiveData.postValue(displayable.isDownloaded())
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            getView()?.apply {
                withContext(Dispatchers.Main) {
                    isDisplayableLiveData.observe(
                        getLifecycleOwner(),
                        Observer { isDisplayable ->
                            if (isDisplayable) {
                                getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                                    displayable.getFile()?.let { file ->
                                        log.debug("file ${file.absolutePath} exists: ${file.exists()}")
                                        getView()?.loadUrl("file://${file.absolutePath}")
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }


    override fun onLinkClicked(webViewDisplayable: WebViewDisplayable) {
        getView()?.getMainView()?.showInWebView(webViewDisplayable)
    }

    override fun onPageFinishedLoading() {
        getView()?.hideLoadingScreen()
    }

    /**
     * This method will help to re-inject css into the WebView upon changes
     * to the corresponding shared preferences
     */
    fun injectCss(sharedPreferences: SharedPreferences) {
        log.debug("Injecting css")

        val cssString = TazApiCssHelper.generateCssString(sharedPreferences)
        val encoded = Base64.encodeToString(cssString.toByteArray(), Base64.NO_WRAP)
        log.debug("Injected css: $cssString")
        getView()?.getWebView()
            ?.evaluateJavascript("(function() {tazApi.injectCss(\"$encoded\");})()", null)
    }

    /**
     * Try to get the resourceInfo from apiService.
     * @return ResourceInfo or null
     */
    private suspend fun tryGetResourceInfo(): ResourceInfo? {
        return try {
            apiService.getResourceInfo()?.let {
                resourceInfoRepository.save(it)
                it
            } ?: run {
                getView()?.getMainView()?.showToast(R.string.something_went_wrong_try_later)
                null
            }
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            getView()?.getMainView()?.showToast(R.string.toast_no_internet)
            null
        }
    }

    /**
     * Check if minimal resource version of the issue is <= the current resource version.
     * @return Boolean if resource info is up to date or not
     */
    private fun isResourceInfoUpToDate(): Boolean {
        val issueOperations = getView()?.getWebViewDisplayable()?.getIssueOperations()

        val issue = IssueRepository.getInstance().getIssueByFeedAndDate(
            issueOperations?.feedName ?: "",
            issueOperations?.date ?: "",
            issueOperations?.status ?: IssueStatus.public
        )

        val minResourceVersion = issue?.minResourceVersion ?: 0
        val currentResourceVersion = resourceInfoRepository.get()?.resourceVersion ?: 0

        return minResourceVersion <= currentResourceVersion
    }

}