package de.taz.app.android.ui.webview

import android.view.MenuItem
import de.taz.app.android.R
import de.taz.app.android.api.models.Section

class SectionWebViewPresenter : WebViewPresenter<Section>() {

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home ->
                getView()?.getMainView()?.showHome()

            R.id.bottom_navigation_action_size ->
                if (activated) {
                    getView()?.showFontSettingBottomSheet()
                } else {
                    getView()?.hideBottomSheet()
                }
        }
    }

    override fun onBackPressed(): Boolean {
        if (getView()?.isBottomSheetVisible() == true) {
            getView()?.hideBottomSheet()
            return true
        }
        return false
    }
}