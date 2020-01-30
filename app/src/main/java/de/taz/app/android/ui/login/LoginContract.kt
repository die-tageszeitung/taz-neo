package de.taz.app.android.ui.login

import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BaseContract

interface LoginContract {
    interface View : BaseContract.View {
        fun showLoadingScreen()
        fun hideLoadingScreen()

        fun showUserNameError(@StringRes usernameErrorId: Int)
        fun showPasswordError(@StringRes passwordErrorId: Int)

        fun showLoginWithEmail()

        fun showSubscriptionElapsed()
    }

    interface Presenter : BaseContract.Presenter {
        fun onBottomNavigationItemClicked(menuItem: MenuItem)

        fun login(username: String, password: String)
    }

    interface DataController : BaseContract.DataController {
        fun observeAuthStatus(
            lifecycleOwner: LifecycleOwner,
            observationCallback: (AuthStatus) -> Unit
        ): Observer<AuthStatus>
    }
}