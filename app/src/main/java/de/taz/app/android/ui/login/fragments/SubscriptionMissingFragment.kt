package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.ui.login.LoginViewModelState
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionBaseFragment
import kotlinx.android.synthetic.main.fragment_login_missing_subscription.*

class SubscriptionMissingFragment : SubscriptionBaseFragment(R.layout.fragment_login_missing_subscription) {

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
        fragment_login_missing_subscription_password.setText(viewModel.subscriptionPassword ?: "")

        if (invalidId) {
            fragment_login_missing_subscription_layout.error = getString(
                R.string.login_subscription_error_invalid
            )
        }

        fragment_login_missing_subscription_connect_account.setOnClickListener {
            ifDoneNext()
        }

        fragment_login_forgot_password_text.setOnClickListener {
            viewModel.requestPasswordReset(subscriptionId = true)
        }

        fragment_login_missing_subscription_password.setOnEditorActionListener(
            OnEditorActionDoneListener(::ifDoneNext)
        )

        fragment_login_missing_subscription_subscription_button.setOnClickListener {
            viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_REQUEST)
        }
    }

    override fun done(): Boolean {
        val subscriptionId = fragment_login_missing_subscription.text.toString().trim()
        val subscriptionPassword = fragment_login_missing_subscription_password.text.toString()

        var somethingWrong = false

        if (subscriptionId.isEmpty()) {
            fragment_login_missing_subscription_layout.error = getString(
                R.string.login_subscription_error_empty
            )
            somethingWrong = true
        } else if (subscriptionId.toIntOrNull() == null) {
            fragment_login_missing_subscription_layout.error = getString(
                R.string.login_subscription_error_invalid
            )
            somethingWrong = true
        }

        if (subscriptionPassword.isEmpty()) {
            fragment_login_missing_subscription_password_layout.error = getString(
                R.string.login_password_error_empty
            )
            somethingWrong = true
        }
        viewModel.subscriptionId = subscriptionId.toInt()
        viewModel.subscriptionPassword = subscriptionPassword

        return !somethingWrong
    }

    override fun next() {
        hideKeyBoard()
        viewModel.connect()
    }

}