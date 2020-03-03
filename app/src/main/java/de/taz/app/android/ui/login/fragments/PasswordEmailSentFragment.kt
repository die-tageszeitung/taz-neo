package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_forgot_password_email_sent.*


class PasswordEmailSentFragment: BaseFragment(R.layout.fragment_login_forgot_password_email_sent) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_forgot_password_email_sent_back.setOnClickListener {
            viewModel.backAfterEmailSent()
        }
    }

}