package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.webkit.WebChromeClient
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.Shareable
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.ui.feed.FeedFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAZ_API_JS = "ANDROIDAPI"

class WebViewPresenter<DISPLAYABLE : WebViewDisplayable> :
    BasePresenter<WebViewContract.View<DISPLAYABLE>, WebViewDataController<DISPLAYABLE>>(
        WebViewDataController<DISPLAYABLE>().javaClass
    ),
    WebViewContract.Presenter {

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
            webContractView.getWebView().apply {
                webViewClient = AppWebViewClient(this@WebViewPresenter)
                webChromeClient = WebChromeClient()
                settings.javaScriptEnabled = true
                addJavascriptInterface(tazApiJS, TAZ_API_JS)
                setArticleWebViewCallback(this@WebViewPresenter)
            }
        }
    }

    private fun observeFile() {
        getView()?.let { view ->
            viewModel?.observeWebViewDisplayable(view.getLifecycleOwner()) { displayable ->
                displayable?.let {
                    getView()?.getMainView()?.getApplicationContext()?.let {
                        view.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                            if (!displayable.isDownloaded()) {
                                DownloadService.download(it, displayable)
                            }

                            val observer = object : Observer<Boolean> {
                                override fun onChanged(isDownloaded: Boolean?) {
                                    if (isDownloaded == true) {
                                        displayable.isDownloadedLiveData().removeObserver(this)
                                        getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                                            displayable.getFile()?.let { file ->
                                                withContext(Dispatchers.Main) {
                                                    getView()?.loadUrl("file://${file.absolutePath}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            withContext(Dispatchers.Main) {
                                displayable.isDownloadedLiveData()
                                    .observe(view.getLifecycleOwner(), observer)
                            }
                        }
                    }
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

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        val webViewDisplayable = viewModel?.getWebViewDisplayable()

        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home ->
                getView()?.getMainView()?.showMainFragment(FeedFragment())

            R.id.bottom_navigation_action_bookmark ->
                if (webViewDisplayable is Article) {
                    getView()?.let { view ->
                        val articleRepository = ArticleRepository.getInstance()
                        if (view.isPermanentlyActive(R.id.bottom_navigation_action_bookmark)) {
                            view.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                                articleRepository.debookmarkArticle(webViewDisplayable)
                            }
                            view.unsetPermanentlyActive(R.id.bottom_navigation_action_bookmark)
                            view.setIconInactive(R.id.bottom_navigation_action_bookmark)
                        } else {
                            view.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                                articleRepository.bookmarkArticle(webViewDisplayable)
                            }
                            view.setPermanentlyActive(R.id.bottom_navigation_action_bookmark)
                            view.setIconActive(R.id.bottom_navigation_action_bookmark)
                        }
                    }
                }

            R.id.bottom_navigation_action_share ->
                if (webViewDisplayable is Shareable) {
                    webViewDisplayable.getLink()?.let {
                        getView()?.apply {
                            shareText(it)
                            setIconInactive(R.id.bottom_navigation_action_share)
                        }
                    }
                }

            R.id.bottom_navigation_action_size ->
                if (activated) {
                    getView()?.showFontSettingBottomSheet()
                } else {
                    getView()?.hideBottomSheet()
                }
        }
    }

    override fun onScrollStarted() {
    }

    override fun onScrollFinished() {
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        return false
    }

    override fun onBackPressed(): Boolean {
        val webViewDisplayable = viewModel?.getWebViewDisplayable()

        return when (webViewDisplayable) {
            is Article -> {
                if (!webViewDisplayable.isImprint()) {
                    getView()?.getMainView()?.let {
                        it.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                            (webViewDisplayable as Article).getSection()?.let { section ->
                                it.showInWebView(section)
                            }
                        }
                        return true
                    }
                }
                return false
            }
            is Section -> {
                getView()?.getMainView()?.let {
                    it.showFeed()
                    true
                } ?: false
            }
            else -> false
        }
    }

}