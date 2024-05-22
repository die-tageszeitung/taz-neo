package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.databinding.FragmentLoginSubscriptionTakenBinding

class SubscriptionTakenFragment: LoginBaseFragment<FragmentLoginSubscriptionTakenBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentLoginSubscriptionTakenRetry.setOnClickListener {
            viewModel.apply {
                backToMissingSubscription()
            }
        }

        viewBinding.fragmentLoginSubscriptionTakenEmail.setOnClickListener {
            writeEmail()
        }

        viewBinding.cancelButton.setOnClickListener {
            loginFlowCancel()
        }
    }
}