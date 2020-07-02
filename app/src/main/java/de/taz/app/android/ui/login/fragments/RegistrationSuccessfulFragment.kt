package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.ui.login.LoginActivity
import kotlinx.android.synthetic.main.fragment_login_confirm_email.*

class RegistrationSuccessfulFragment: LoginBaseFragment(R.layout.fragment_login_registration_successful) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideKeyBoard()

        if (!viewModel.backToArticle) {
            fragment_login_confirm_done.text = getString(
                R.string.fragment_login_success_login_back_coverflow
            )
        }

        fragment_login_confirm_done.setOnClickListener {
            (activity as? LoginActivity)?.done() ?: activity?.finish()
        }
    }
}