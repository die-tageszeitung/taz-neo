package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.markRequired
import kotlinx.android.synthetic.main.fragment_login_request_test_subscription.*

class RequestTestSubscriptionFragment :
    BaseFragment(R.layout.fragment_login_request_test_subscription) {

    private var invalidMail: Boolean = false

    companion object {
        fun create(invalidMail: Boolean): RequestTestSubscriptionFragment {
            val fragment = RequestTestSubscriptionFragment()
            fragment.invalidMail = invalidMail
            return fragment
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.username?.let {
            fragment_login_request_test_subscription_email.setText(it)
        }

        viewModel.password?.let {
            fragment_login_request_test_subscription_password.setText(it)
        }

        fragment_login_request_test_subscription_email_layout.markRequired()
        fragment_login_request_test_subscription_password_layout.markRequired()
        fragment_login_request_test_subscription_password_confirmation_layout.markRequired()
        fragment_login_request_test_subscription_first_name_layout.markRequired()
        fragment_login_request_test_subscription_surname_layout.markRequired()

        fragment_login_request_test_subscription_login.setOnClickListener {
            requestSubscription()
        }

        fragment_login_request_test_subscription_surname.setOnEditorActionListener(
            OnEditorActionDoneListener(::requestSubscription)
        )

    }

    private fun requestSubscription() {
        val email = fragment_login_request_test_subscription_email.text.toString().trim()
        val password = fragment_login_request_test_subscription_password.text.toString()

        val passwordConfirm =
            fragment_login_request_test_subscription_password_confirmation.text.toString()
        val firstName = fragment_login_request_test_subscription_first_name.text.toString().trim()
        val surname = fragment_login_request_test_subscription_surname.text.toString().trim()

        var somethingWrong = false

        if (passwordConfirm.isNotEmpty()) {
            if (password != passwordConfirm) {
                fragment_login_request_test_subscription_password_layout.error = getString(
                    R.string.login_password_confirmation_error_match
                )
                somethingWrong = true
            }
            if (firstName.isEmpty()) {
                fragment_login_request_test_subscription_first_name_layout.error = getString(
                    R.string.login_first_name_error_empty
                )
                somethingWrong = true
            }
            if (surname.isEmpty()) {
                fragment_login_request_test_subscription_surname_layout.error = getString(
                    R.string.login_surname_error_empty
                )
                somethingWrong = true
            }
        }

        if (email.isEmpty()) {
            fragment_login_request_test_subscription_email_layout.error = getString(
                R.string.login_email_error_empty
            )
            somethingWrong = true
        } else {
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                fragment_login_request_test_subscription_email_layout.error = getString(
                    R.string.login_email_error_no_email
                )
                somethingWrong = true
            }
        }

        if (password.isEmpty()) {
            if (password.isEmpty()) {
                fragment_login_request_test_subscription_password_layout.error = getString(
                    R.string.login_password_error_empty
                )
            }
            somethingWrong = true
        }
        if (somethingWrong) {
            return
        } else {
            hideKeyBoard()
            viewModel.getTrialSubscriptionForNewCredentials(email, password, firstName, surname)
        }
    }

}