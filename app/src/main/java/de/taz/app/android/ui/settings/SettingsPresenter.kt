package de.taz.app.android.ui.settings

import android.os.Bundle
import android.view.MenuItem
import de.taz.app.android.R
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.ui.home.HomeFragment

class SettingsPresenter : BasePresenter<SettingsOuterFragment, SettingsDataController>(SettingsDataController::class.java),
      SettingsContract.Presenter{

    override fun onViewCreated(savedInstanceState: Bundle?) {

    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.bottom_navigation_action_home) {
            getView()?.getMainView()?.showHome()
        }
    }
}