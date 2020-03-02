package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.android.synthetic.main.fragment_login_missing_subscription.*

class MissingSubscriptionFragment: BaseFragment(R.layout.fragment_login_missing_subscription) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

       fragment_login_missing_subscription_test_subscription.setOnClickListener {
           lazyViewModel.value.status.postValue(LoginViewModelState.SUBSCRIPTION_REQUESTING)
           lazyViewModel.value.register()
       }
    }

}