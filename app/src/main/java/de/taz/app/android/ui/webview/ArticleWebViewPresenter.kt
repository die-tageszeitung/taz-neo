package de.taz.app.android.ui.webview

import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.Shareable
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArticleWebViewPresenter(
    apiService: ApiService = ApiService.getInstance(),
    resourceInfoRepository: ResourceInfoRepository = ResourceInfoRepository.getInstance()
) : WebViewPresenter<Article>(apiService, resourceInfoRepository) {

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        val webViewDisplayable = viewModel?.getWebViewDisplayable()

        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home ->
                getView()?.getMainView()?.showHome()

            R.id.bottom_navigation_action_bookmark ->
                getView()?.apply {
                    showBookmarkBottomSheet()
                }

            R.id.bottom_navigation_action_share ->
                if (webViewDisplayable is Shareable) {
                    webViewDisplayable.getLink()?.let {
                        getView()?.apply {
                            shareText(it)
                        }
                    }
                }

            R.id.bottom_navigation_action_size ->
                getView()?.showFontSettingBottomSheet()
        }
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

    override fun onBackPressed(): Boolean {
        return false
    }

}