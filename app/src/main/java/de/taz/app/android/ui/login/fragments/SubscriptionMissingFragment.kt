package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_missing_subscription.*

class SubscriptionMissingFragment: BaseFragment(R.layout.fragment_login_missing_subscription) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

       fragment_login_missing_subscription_test_subscription.setOnClickListener {
           lazyViewModel.value.register()
       }
    }

}