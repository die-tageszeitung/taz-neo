package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import kotlinx.android.synthetic.main.fragment_login_missing_credentials.*

class CredentialsMissingFragment : BaseFragment(R.layout.fragment_login_missing_credentials) {

    private var invalidMail: Boolean = true

    companion object {
        fun create(invalidMail: Boolean): CredentialsMissingFragment {
            val fragment = CredentialsMissingFragment()
            fragment.invalidMail = invalidMail
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.username?.let {
            fragment_login_missing_credentials_email.setText(it)
        }

        if (invalidMail) {
            fragment_login_missing_credentials_email_layout.error = getString(
                R.string.login_email_error_no_email
            )
        }

        fragment_login_missing_credentials_login.setOnClickListener {
            connect()
        }

        fragment_login_missing_credentials_forgot_password_button?.setOnClickListener {
            viewModel.requestPasswordReset()
        }

        fragment_login_missing_credentials_surname.setOnEditorActionListener(
            OnEditorActionDoneListener(::connect)
        )

    }

    private fun connect() {
        val email = fragment_login_missing_credentials_email.text.toString()
        val password = fragment_login_missing_credentials_password.text.toString()

        val passwordConfirm =
            fragment_login_missing_credentials_password_confirmation.text.toString()
        val firstName = fragment_login_missing_credentials_first_name.text.toString()
        val surname = fragment_login_missing_credentials_surname.text.toString()

        if (passwordConfirm.isNotEmpty()) {
            if (password != passwordConfirm) {
                fragment_login_missing_credentials_password_layout.error = getString(
                    R.string.login_password_confirmation_error_match
                )
                return
            }
            if (firstName.isEmpty()) {
                fragment_login_missing_credentials_first_name_layout.error = getString(
                    R.string.login_first_name_error_empty
                )
                return
            }
            if (surname.isEmpty()) {
                fragment_login_missing_credentials_surname_layout.error = getString(
                    R.string.login_surname_error_empty
                )
                return
            }
        }

        if (email.isEmpty()) {
            fragment_login_missing_credentials_email_layout.error = getString(
                R.string.login_email_error_empty
            )
            return
        } else {
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                fragment_login_missing_credentials_email_layout.error = getString(
                    R.string.login_email_error_no_email
                )
                return
            }
        }

        if (password.isEmpty()) {
            if (password.isEmpty()) {
                fragment_login_missing_credentials_password_layout.error = getString(
                    R.string.login_password_error_empty
                )
            }
            return
        }

        viewModel.connect(
            username = email,
            password = password,
            firstName = firstName,
            surname = surname
        )
    }

}