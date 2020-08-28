package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.android.synthetic.main.fragment_login_subscription_already_taken.*

class SubscriptionAlreadyLinkedFragment : LoginBaseFragment(R.layout.fragment_login_subscription_already_taken) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_subscription_already_taken_insert_new.setOnClickListener {
            viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
        }
        fragment_login_subscription_already_taken_contact_email.setOnClickListener {
            writeEmail()
        }
    }

}