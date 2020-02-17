package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_missing_credentials.*

class CredentialsMissingFragment : BaseFragment(R.layout.fragment_login_missing_credentials) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.username?.let {
           fragment_login_missing_credentials_email_text.setText(it)
        }


        fragment_login_missing_credentials_login.setOnClickListener {
            viewModel.connect(
                fragment_login_missing_credentials_email_text.text.toString(),
                fragment_login_missing_credentials_password_text.text.toString()
            )
        }

        fragment_login_missing_credentials_forgot_password_button?.setOnClickListener {
            viewModel.resetCredentialsPassword(
                fragment_login_missing_credentials_email_text.text.toString()
            )
        }

        fragment_login_missing_credentials_forgot_password.setOnClickListener {
            val email = fragment_login_missing_credentials_email_text.toString()
            viewModel.resetCredentialsPassword(email)
        }
    }

}