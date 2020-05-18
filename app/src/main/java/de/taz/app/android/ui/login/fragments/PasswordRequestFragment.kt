package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import kotlinx.android.synthetic.main.fragment_login_forgot_password.*

class PasswordRequestFragment : BaseFragment(R.layout.fragment_login_forgot_password) {

    private var invalidId: Boolean = false

    companion object{
        fun create(invalidId: Boolean = false): PasswordRequestFragment {
            val fragment = PasswordRequestFragment()
            fragment.invalidId = invalidId
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(invalidId) {
            fragment_login_forgot_password_username_layout.error = getString(
                R.string.login_forgot_password_error_invalid_id
            )
        }

        fragment_login_forgot_password_button.setOnClickListener {
            requestPasswordReset()
        }

        fragment_login_forgot_password_username.setOnEditorActionListener(
            OnEditorActionDoneListener(::requestPasswordReset)
        )

    }

    private fun requestPasswordReset() {
        val username = fragment_login_forgot_password_username.text.toString().trim()
        if (username.isEmpty()) {
            fragment_login_forgot_password_username_layout.error =
                getString(R.string.login_username_error_empty)
        } else {

            if (username.toIntOrNull() != null) {
                hideKeyBoard()
                viewModel.requestSubscriptionPassword(username.toInt())
            } else if( android.util.Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
                hideKeyBoard()
                viewModel.requestCredentialsPasswordReset(username)
            } else {
                fragment_login_forgot_password_username_layout.error =
                    getString(R.string.login_email_error_no_email)
            }
        }
    }
}