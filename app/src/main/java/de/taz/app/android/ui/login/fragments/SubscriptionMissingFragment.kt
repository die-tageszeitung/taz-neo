package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_missing_subscription.*

class SubscriptionMissingFragment: BaseFragment(R.layout.fragment_login_missing_subscription) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

       fragment_login_missing_subscription_connect_account.setOnClickListener {

           viewModel.connect(
              intialSubscriptionId = fragment_login_missing_subscription.text.toString().toIntOrNull(),
              initialSubscriptionPassword = fragment_login_missing_subscription_password.text.toString()
           )
       }

        fragment_login_missing_subscription_test_subscription.setOnClickListener {
            viewModel.register()
        }
    }

}