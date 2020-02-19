package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_forgot_password.*

class PasswordRequestFragment : BaseFragment(R.layout.fragment_login_forgot_password) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_forgot_password_button.setOnClickListener {
            val username = fragment_login_forgot_password_username.text.toString()
            if (username.isEmpty()) {
                fragment_login_forgot_password_username_layout.error =
                    getString(R.string.login_username_error_empty)
            } else {

                if (username.toIntOrNull() != null)
                    viewModel.requestSubscriptionPassword(username.toInt())
                else if( android.util.Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
                    viewModel.requestCredentialsPasswordReset(username)
                } else {
                    fragment_login_forgot_password_username_layout.error =
                        getString(R.string.login_email_error_no_email)
                }
            }
        }
    }
}