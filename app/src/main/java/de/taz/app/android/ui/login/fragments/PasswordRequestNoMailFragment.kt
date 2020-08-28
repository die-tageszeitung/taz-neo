package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import de.taz.app.android.R
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.android.synthetic.main.fragment_login_forgot_password_no_mail.*

class PasswordRequestNoMailFragment :
    LoginBaseFragment(R.layout.fragment_login_forgot_password_no_mail) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fragment_login_forgot_password_no_mail_email.setOnClickListener {
            writeEmail(fragment_login_forgot_password_no_mail_email.text?.toString() ?: "")
        }
        fragment_login_forgot_password_no_mail_cancel.setOnClickListener {
            viewModel.status.postValue(LoginViewModelState.INITIAL)
        }

    }
}