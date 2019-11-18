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
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

const val TAZ_API_JS = "ANDROIDAPI"

class WebViewPresenter<DISPLAYABLE: WebViewDisplayable>:
    BasePresenter<WebViewContract.View<DISPLAYABLE>, WebViewDataController<DISPLAYABLE>>(WebViewDataController<DISPLAYABLE>().javaClass),
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
            viewModel?.fileLiveData?.observe(view.getLifecycleOwner(), Observer { file ->
                file?.let {
                    view.loadUrl("file://${file.absolutePath}")
                }
            })
        }
    }

    override fun onLinkClicked(webViewDisplayable: WebViewDisplayable) {
        getView()?.getMainView()?.showInWebView(webViewDisplayable)
    }

    override fun onPageFinishedLoading() {
        getView()?.hideLoadingScreen()
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        val webViewDisplayable = viewModel?.getWebViewDisplayable()

        if (
            menuItem.itemId == R.id.bottom_navigation_action_bookmark && webViewDisplayable is Article
        ) {
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

        if(menuItem.itemId == R.id.bottom_navigation_action_share && webViewDisplayable is Shareable) {
            webViewDisplayable.getLink()?.let {
                getView()?.apply {
                    shareText(it)
                    setIconInactive(R.id.bottom_navigation_action_share)
                }
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

    override fun onSwipeLeft(): Job? {
        getView()?.let { view ->
            return view.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                viewModel?.getWebViewDisplayable()?.next()?.let { next ->
                    view.getMainView()
                        ?.showInWebView(next, R.anim.slide_in_left, R.anim.slide_out_left)
                }
            }
        }
        return null
    }

    override fun onSwipeRight(): Job? {
        getView()?.let { view ->
            return view.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                viewModel?.getWebViewDisplayable()?.previous()?.let { previous ->
                    view.getMainView()
                        ?.showInWebView(previous, R.anim.slide_in_right, R.anim.slide_out_right)
                }
            }
        }
        return null
    }

    override fun onSwipeTop() {
    }

    override fun onSwipeBottom() {
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
                    it.showArchive()
                    true
                }?: false
            }
            else -> false
        }
    }

}