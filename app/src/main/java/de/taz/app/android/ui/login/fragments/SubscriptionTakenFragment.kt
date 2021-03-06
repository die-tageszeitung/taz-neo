package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_subscription_taken.*

class SubscriptionTakenFragment: LoginBaseFragment(R.layout.fragment_login_subscription_taken) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_subscription_taken_retry.setOnClickListener {
            viewModel.apply {
                backToMissingSubscription()
            }
        }

        fragment_login_subscription_taken_email.setOnClickListener {
            writeEmail()
        }
    }

}