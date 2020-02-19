package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_missing_subscription.*

class SubscriptionMissingFragment : BaseFragment(R.layout.fragment_login_missing_subscription) {

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

        fragment_login_missing_subscription.setText(viewModel.subscriptionId?.toString() ?: "")
        fragment_login_missing_subscription_password.setText(viewModel.subscriptionPassword?: "")

        if (invalidId) {
            fragment_login_missing_subscription.error = getString(
                R.string.login_subscription_error_invalid
            )
        }

        fragment_login_missing_subscription_connect_account.setOnClickListener {
            val subscriptionId = fragment_login_missing_subscription.text.toString()
            val subscriptionPassword = fragment_login_missing_subscription_password.text.toString()

            if (subscriptionId.isEmpty()) {
                fragment_login_missing_subscription.error = getString(
                    R.string.login_subscription_error_empty
                )
                return@setOnClickListener
            } else if (subscriptionId.toIntOrNull() == null) {
                fragment_login_missing_subscription.error = getString(
                    R.string.login_subscription_error_invalid
                )
                return@setOnClickListener
            }

            if (subscriptionPassword.isEmpty()) {
                fragment_login_missing_subscription_password.error = getString(
                    R.string.login_password_error_empty
                )
                return@setOnClickListener
            }

            viewModel.connect(
                subscriptionId = subscriptionId.toIntOrNull(),
                subscriptionPassword =  subscriptionPassword
            )
        }

        fragment_login_forgot_password_button.setOnClickListener {
            viewModel.requestPasswordReset()
        }


        fragment_login_missing_subscription_test_subscription.setOnClickListener {
            viewModel.getTrialSubscriptionForExistingCredentials()
        }
    }

}