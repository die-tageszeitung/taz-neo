package de.taz.app.android.ui.login

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.util.AuthHelper

class LoginDataController: BaseDataController(), LoginContract.DataController {

    private val authStatus = Transformations.distinctUntilChanged(
        AuthHelper.getInstance().authStatusLiveData
    )

    override fun observeAuthStatus(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (AuthStatus?) -> Unit
    ) {
        authStatus.observe(lifecycleOwner, Observer(observationCallback))
    }

}