package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentLoginMissingSubscriptionBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionBaseFragment
import de.taz.app.android.util.hideSoftInputKeyboard

class SubscriptionMissingFragment :
    SubscriptionBaseFragment<FragmentLoginMissingSubscriptionBinding>() {

    private var invalidId: Boolean = false

    companion object {
        fun create(invalidId: Boolean): SubscriptionMissingFragment {
            val fragment = SubscriptionMissingFragment()
            fragment.invalidId = invalidId
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentLoginMissingSubscription.setText(viewModel.subscriptionId?.toString() ?: "")
        viewBinding.fragmentLoginMissingSubscriptionPassword.setText(viewModel.subscriptionPassword ?: "")

        if (invalidId) {
            viewBinding.fragmentLoginMissingSubscriptionLayout.error = getString(
                R.string.login_subscription_error_invalid
            )
        }

        viewBinding.fragmentLoginMissingSubscriptionConnectAccount.setOnClickListener {
            ifDoneNext()
        }

        viewBinding.backButton.setOnClickListener {
            loginFlowBack()
        }

        viewBinding.fragmentLoginMissingSubscriptionPassword.setOnEditorActionListener(
            OnEditorActionDoneListener(::ifDoneNext)
        )

        viewBinding.fragmentLoginMissingSubscriptionHelp.setOnClickListener {
            showHelpDialog(R.string.fragment_login_missing_subscription_help)
        }

    }

    override fun done(): Boolean {
        val subscriptionId = viewBinding.fragmentLoginMissingSubscription.text.toString().trim()
        val subscriptionPassword = viewBinding.fragmentLoginMissingSubscriptionPassword.text.toString()

        var somethingWrong = false

        if (subscriptionId.isEmpty()) {
            viewBinding.fragmentLoginMissingSubscriptionLayout.error = getString(
                R.string.login_subscription_error_empty
            )
            somethingWrong = true
        } else if (subscriptionId.toIntOrNull() == null) {
            viewBinding.fragmentLoginMissingSubscriptionLayout.error = getString(
                R.string.login_subscription_error_invalid
            )
            somethingWrong = true
        }

        if (subscriptionPassword.isEmpty()) {
            viewBinding.fragmentLoginMissingSubscriptionPasswordLayout.error = getString(
                R.string.login_password_error_empty
            )
            somethingWrong = true
        }
        viewModel.subscriptionId = subscriptionId.toInt()
        viewModel.subscriptionPassword = subscriptionPassword

        return !somethingWrong
    }

    override fun next() {
        hideSoftInputKeyboard()
        viewModel.connect()
    }

}