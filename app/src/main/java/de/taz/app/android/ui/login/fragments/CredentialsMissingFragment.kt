package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.android.synthetic.main.fragment_login_missing_credentials.*

class CredentialsMissingFragment : BaseFragment(R.layout.fragment_login_missing_credentials) {

    private var failed: Boolean = false
    private var registration: Boolean = true

    companion object {
        fun create(
            registration: Boolean,
            failed: Boolean = false
        ): CredentialsMissingFragment {
            val fragment = CredentialsMissingFragment()
            fragment.failed = failed
            fragment.registration = registration
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!registration) {
            fragment_login_missing_credentials_password_confirmation_layout.visibility = View.GONE
            fragment_login_missing_credentials_first_name_layout.visibility = View.GONE
            fragment_login_missing_credentials_surname_layout.visibility = View.GONE
            fragment_login_missing_credentials_password.imeOptions = EditorInfo.IME_ACTION_DONE
        } else {
            fragment_login_missing_credentials_forgot_password_button.visibility = View.GONE
            fragment_login_missing_credentials_forgot_password.visibility = View.GONE
        }

        fragment_login_missing_credentials_switch.setOnClickListener {
            viewModel.status.postValue(
                if (registration) {
                    LoginViewModelState.CREDENTIALS_MISSING_LOGIN
                } else {
                    LoginViewModelState.CREDENTIALS_MISSING_REGISTER
                }
            )
        }

        if (!registration) {
            fragment_login_missing_credentials_switch.text =
                getString(R.string.fragment_login_missing_credentials_switch_to_registration)

            fragment_login_missing_credentials_header.text =
                getString(R.string.fragment_login_missing_credentials_header_login)
        }

        viewModel.username?.let {
            fragment_login_missing_credentials_email.setText(it)
        }

        if (failed) {
            fragment_login_missing_credentials_email_layout.error = getString(
                R.string.login_failed
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

        fragment_login_missing_credentials_password.setOnEditorActionListener(
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
            fragment_login_missing_credentials_password_layout.error = getString(
                R.string.login_password_error_empty
            )
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