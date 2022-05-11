package de.taz.app.android.ui.login.fragments.subscription

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.android.synthetic.main.fragment_subscription_price.*


class SubscriptionTrialOnlyFragment : SubscriptionBaseFragment(R.layout.fragment_subscription_trial_only) {
    companion object {
        fun createInstance(
        ): SubscriptionTrialOnlyFragment {
            return SubscriptionTrialOnlyFragment()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_subscription_address_proceed.setOnClickListener { ifDoneNext() }
    }

    override fun done(): Boolean {
        viewModel.price = 0
        return true
    }

    override fun next() {
        viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS)
    }

}