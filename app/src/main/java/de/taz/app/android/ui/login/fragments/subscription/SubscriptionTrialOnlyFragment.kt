package de.taz.app.android.ui.login.fragments.subscription

import android.content.Context
import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentSubscriptionTrialOnlyBinding
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
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (elapsed) {
            viewBinding.apply {
                fragmentSubscriptionTrialOnlyDescription.setText(R.string.fragment_subscription_trial_only_description_elapsed)
                fragmentSubscriptionTrialOnlyProceed.apply {
                    setText(R.string.close_okay)
                    setOnClickListener {
                        loginFlowDone()
                    }
                }
            }
        } else {
            viewBinding.apply {
                fragmentSubscriptionTrialOnlyDescription.setText(R.string.fragment_subscription_trial_only_description)
                fragmentSubscriptionTrialOnlyProceed.apply {
                    setText(R.string.next_button)
                    setOnClickListener {
                        ifDoneNext()
                    }
                }
            }
        }

        viewBinding.backButton.setOnClickListener {
            loginFlowBack()
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
        return true
    }

    override fun next() {
        viewModel.status = LoginViewModelState.SUBSCRIPTION_NAME
    }
}