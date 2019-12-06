package de.taz.app.android.ui.login

import android.view.MenuItem
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BaseContract

interface LoginContract {
    interface View : BaseContract.View {
        fun showLoadingScreen()
        fun hideLoadingScreen()
    }

    interface Presenter : BaseContract.Presenter {
        fun onBackPressed(): Boolean

        fun onBottomNavigationItemClicked(menuItem: MenuItem)

        fun login(username: String, password: String)
    }

    interface DataController : BaseContract.DataController {
        fun observeAuthStatus(lifecycleOwner: LifecycleOwner, observationCallback: (AuthStatus?) -> Unit)
    }
}