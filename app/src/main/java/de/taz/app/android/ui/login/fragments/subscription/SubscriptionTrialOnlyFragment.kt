package de.taz.app.android.ui.login.fragments.subscription

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentSubscriptionTrialOnlyBinding
import de.taz.app.android.ui.login.LoginViewModelState


class SubscriptionTrialOnlyFragment : SubscriptionBaseFragment<FragmentSubscriptionTrialOnlyBinding>() {
    private var elapsed: Boolean = false
    companion object {
        fun newInstance(
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
            viewBinding.fragmentSubscriptionTrialOnlyDescription.visibility = View.GONE
            viewBinding.fragmentSubscriptionTrialOnlyDescriptionElapsed.visibility = View.VISIBLE
            viewBinding.fragmentSubscriptionAddressProceed.text =
                getString(R.string.close_okay)
            viewBinding.fragmentSubscriptionAddressProceed.setOnClickListener { this.activity?.finish() }
        } else {
            viewBinding.fragmentSubscriptionAddressProceed.setOnClickListener { ifDoneNext() }
        }
        viewBinding.cancelButton.setOnClickListener { back() }
    }

    override fun done(): Boolean {
        viewModel.price = 0
        return true
    }

    override fun next() {
        viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS)
    }
}