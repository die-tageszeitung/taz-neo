package de.taz.app.android.ui.login

import android.view.MenuItem
import de.taz.app.android.base.BaseContract

interface LoginContract {
    interface Presenter : BaseContract.Presenter {
        fun onBackPressed(): Boolean
        fun onBottomNavigationItemClicked(menuItem: MenuItem)
    }

    interface DataController : BaseContract.DataController
}