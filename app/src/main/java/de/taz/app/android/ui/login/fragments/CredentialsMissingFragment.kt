package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_missing_credentials.*

class CredentialsMissingFragment : BaseFragment(R.layout.fragment_login_missing_credentials) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.username?.let {
            fragment_login_missing_credentials_email_text.setText(it)
        }


        fragment_login_missing_credentials_login.setOnClickListener {
            connect()
        }

        fragment_login_missing_credentials_forgot_password_button?.setOnClickListener {
            viewModel.requestPasswordReset(
                fragment_login_missing_credentials_email_text.text.toString()
            )
        }

        fragment_login_missing_credentials_forgot_password.setOnClickListener {
            val email = fragment_login_missing_credentials_email_text.toString()
            viewModel.requestPasswordReset(email)
        }

        fragment_login_missing_credentials_surname.setOnEditorActionListener(
            object : TextView.OnEditorActionListener {
                override fun onEditorAction(
                    v: TextView?,
                    actionId: Int,
                    event: KeyEvent?
                ): Boolean {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        connect()
                        return true
                    }
                    return false
                }
            }
        )

    }

    private fun connect() {
        val email = fragment_login_missing_credentials_email_text.text.toString()
        val password = fragment_login_missing_credentials_password.text.toString()

        val passwordConfirm =
            fragment_login_missing_credentials_password_confirmation.text.toString()
        val firstName = fragment_login_missing_credentials_first_name.text.toString()
        val surName = fragment_login_missing_credentials_surname.text.toString()

        if (passwordConfirm.isNotEmpty()) {
            if (password != passwordConfirm) {
                fragment_login_missing_credentials_password.error = getString(
                    R.string.login_password_confirmation_error_match
                )
                return
            }
            if (firstName.isEmpty()) {
                fragment_login_missing_credentials_first_name.error = getString(
                    R.string.login_first_name_error_empty
                )
                return
            }
            if (surName.isEmpty()) {
                fragment_login_missing_credentials_surname.error = getString(
                    R.string.login_surname_error_empty
                )
                return
            }
        }

        val isEmailEmpty = email.isEmpty()
        val isPasswordEmpty = password.isEmpty()

        if (isEmailEmpty || isPasswordEmpty) {
            if (isEmailEmpty) {
                fragment_login_missing_credentials_email_text.error = getString(
                    R.string.login_email_error_empty
                )
            }
            if (password.isEmpty()) {
                fragment_login_missing_credentials_password.error = getString(
                    R.string.login_password_error_empty
                )
            }
            return
        }

        viewModel.connect(email, password)
    }

}