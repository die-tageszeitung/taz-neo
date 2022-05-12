package de.taz.app.android.ui.login.fragments.subscription

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.android.synthetic.nonfree.fragment_subscription_trial_only.*


class SubscriptionTrialOnlyFragment : SubscriptionBaseFragment(R.layout.fragment_subscription_trial_only) {
    private var elapsed: Boolean = false
    companion object {
        fun createInstance(
            elapsed: Boolean
        ): SubscriptionTrialOnlyFragment {
            val fragment = SubscriptionTrialOnlyFragment()
            fragment.elapsed = elapsed
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (elapsed) {
            fragment_subscription_trial_only_description.visibility = View.GONE
            fragment_subscription_trial_only_description_elapsed.visibility = View.VISIBLE
            fragment_subscription_address_proceed.text =
                getString(R.string.popup_login_elapsed_cancel_button)
            fragment_subscription_address_proceed.setOnClickListener { this.activity?.finish() }
        } else {
            fragment_subscription_address_proceed.setOnClickListener { ifDoneNext() }
        }
    }

    override fun done(): Boolean {
        viewModel.price = 0
        return true
    }

    override fun next() {
        viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS)
    }
}