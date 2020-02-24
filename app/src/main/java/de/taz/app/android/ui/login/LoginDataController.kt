package de.taz.app.android.ui.login

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.singletons.AuthHelper

class LoginDataController: BaseDataController(), LoginContract.DataController {

    override fun observeAuthStatus(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (AuthStatus) -> Unit
    ): Observer<AuthStatus> {
        return AuthHelper.getInstance().observeAuthStatus(lifecycleOwner, observationCallback)
    }

}