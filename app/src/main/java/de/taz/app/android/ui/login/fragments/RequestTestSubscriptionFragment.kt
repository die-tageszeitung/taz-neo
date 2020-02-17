package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_request_test_subscription.*

class RequestTestSubscriptionFragment: BaseFragment(R.layout.fragment_login_request_test_subscription) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.username?.let {
            fragment_login_request_test_subscription_email.setText(it)
        }

        viewModel.password?.let {
           fragment_login_request_test_subscription_password.setText(it)
        }

        fragment_login_request_test_subscription_login.setOnClickListener {
            val password = fragment_login_request_test_subscription_password.text.toString()
            val passwordConfirmation = fragment_login_request_test_subscription_password_confirmation.text.toString()
            val username = fragment_login_request_test_subscription_email.text.toString()

            if (username.isNotEmpty()) {
                if (password.isNotEmpty()) {
                    if (password != passwordConfirmation) {
                        fragment_login_request_test_subscription_password_confirmation.error =
                            getString(
                                R.string.login_password_confirmation_error_match
                            )
                    } else {
                        viewModel.register(username, password)
                    }
                } else {
                    fragment_login_request_test_subscription_password.error = getString(
                        R.string.login_password_error_empty
                    )
                }
            } else {
                fragment_login_request_test_subscription_email.error = getString(
                    R.string.login_email_error_empty
                )
            }
        }

    }

}