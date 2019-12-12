package de.taz.app.android.ui.settings

import android.view.MenuItem
import de.taz.app.android.base.BaseContract

interface SettingsContract {

    interface Presenter: BaseContract.Presenter {
        fun onBottomNavigationItemClicked(menuItem: MenuItem)
    }
}
