package de.taz.app.android.ui.login.fragments.subscription

import android.content.Context
import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentSubscriptionTrialOnlyBinding
import de.taz.app.android.getTazApplication
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.login.LoginViewModelState


class SubscriptionTrialOnlyFragment : SubscriptionBaseFragment<FragmentSubscriptionTrialOnlyBinding>() {
    private lateinit var tracker: Tracker

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = getTazApplication().tracker
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (elapsed) {
            viewBinding.fragmentSubscriptionTrialOnlyDescription.visibility = View.GONE
            viewBinding.fragmentSubscriptionTrialOnlyDescriptionElapsed.visibility = View.VISIBLE
            viewBinding.fragmentSubscriptionAddressProceed.text =
                getString(R.string.close_okay)
            viewBinding.fragmentSubscriptionAddressProceed.setOnClickListener {
                tracker.trackAgreeTappedEvent()
                this.activity?.finish()
            }
        } else {
            viewBinding.fragmentSubscriptionAddressProceed.setOnClickListener {
                tracker.trackContinueTappedEvent()
                ifDoneNext()
            }
        }
        viewBinding.cancelButton.setOnClickListener {
            tracker.trackCancelTappedEvent()
            back()
        }
    }

    override fun onResume() {
        super.onResume()
        if (elapsed) {
            tracker.trackSubscriptionTrialElapsedInfoScreen()
        } else {
            tracker.trackSubscriptionTrialInfoScreen()
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