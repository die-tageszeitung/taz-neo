package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_confirm_email.*

class ConfirmEmailFragment: LoginBaseFragment(R.layout.fragment_login_confirm_email) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideKeyBoard()

        if (!viewModel.backToArticle) {
            fragment_login_confirm_done.text = getString(
                R.string.fragment_login_success_login_back_settings
            )
        }

        if (viewModel.isElapsed()) {
            fragment_login_confirm_email_header.text = getString(
                R.string.fragment_login_confirm_email_elapsed_header
            )
        }

        fragment_login_confirm_done.setOnClickListener {
            viewModel.startPolling()
        }
    }

}