package de.taz.app.android.ui.webview

import android.view.MenuItem
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import de.taz.app.android.ui.home.HomeFragment

class SectionWebViewPresenter : WebViewPresenter<Section>() {

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home ->
                getView()?.getMainView()?.showMainFragment(HomeFragment())

            R.id.bottom_navigation_action_size ->
                if (activated) {
                    getView()?.showFontSettingBottomSheet()
                } else {
                    getView()?.hideBottomSheet()
                }
        }
    }

    override fun onBackPressed(): Boolean {
        return getView()?.getMainView()?.let {
            it.showFeed()
            true
        } ?: false
    }

}