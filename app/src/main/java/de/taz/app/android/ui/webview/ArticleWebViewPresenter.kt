package de.taz.app.android.ui.webview

import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.Shareable
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.ui.feed.FeedFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArticleWebViewPresenter : WebViewPresenter<Article>() {

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        val webViewDisplayable = viewModel?.getWebViewDisplayable()

        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home ->
                getView()?.getMainView()?.showMainFragment(FeedFragment())

            R.id.bottom_navigation_action_bookmark ->
                getView()?.let { view ->
                    val articleRepository = ArticleRepository.getInstance()
                    if (view.isPermanentlyActive(R.id.bottom_navigation_action_bookmark)) {
                        view.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                            webViewDisplayable?.let {
                                articleRepository.debookmarkArticle(webViewDisplayable)
                            }
                        }
                        view.unsetPermanentlyActive(R.id.bottom_navigation_action_bookmark)
                        view.setIconInactive(R.id.bottom_navigation_action_bookmark)
                    } else {
                        view.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                            webViewDisplayable?.let {
                                articleRepository.bookmarkArticle(webViewDisplayable)
                            }
                        }
                        view.setPermanentlyActive(R.id.bottom_navigation_action_bookmark)
                        view.setIconActive(R.id.bottom_navigation_action_bookmark)
                    }
                    getView()?.showBookmarkBottomSheet()
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

    override fun onBackPressed(): Boolean {
        val webViewDisplayable = viewModel?.getWebViewDisplayable()
        if (webViewDisplayable?.isImprint() == false) {
                showSection()
                return true
            }
        return false
    }

    fun showSection() {
        val webViewDisplayable = viewModel?.getWebViewDisplayable()

        getView()?.getMainView()?.let {
            it.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                webViewDisplayable?.getSection()?.let { section ->
                    it.showInWebView(section)
                }
            }
        }
    }

}