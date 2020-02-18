package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_forgot_password.*

class PasswordRequestFragment : BaseFragment(R.layout.fragment_login_forgot_password) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_forgot_password_button.setOnClickListener {
            val email = fragment_login_forgot_password_email.text.toString()
            if (email.isEmpty()) {
                fragment_login_forgot_password_email_layout.error =
                    getString(R.string.login_email_error_empty)
            } else {
                if (email.toIntOrNull() != null || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    viewModel.requestPasswordReset(email)
                } else {
                    fragment_login_forgot_password_email_layout.error =
                        getString(R.string.login_email_error_no_email)
                }
            }
        }
    }
}