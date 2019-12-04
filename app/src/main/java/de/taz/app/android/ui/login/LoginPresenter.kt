package de.taz.app.android.ui.login

import android.os.Bundle
import android.view.MenuItem
import de.taz.app.android.R
import de.taz.app.android.base.BasePresenter

class LoginPresenter :
    BasePresenter<LoginFragment, LoginDataController>(LoginDataController::class.java),
    LoginContract.Presenter {

    override fun onViewCreated(savedInstanceState: Bundle?) {

    }

    override fun onBackPressed(): Boolean {
        getView()?.getMainView()?.showFeed()
        return true
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.bottom_navigation_action_home) {
            getView()?.getMainView()?.showFeed()
        }
    }
}