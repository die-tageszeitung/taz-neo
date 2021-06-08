package de.taz.app.android.ui.login.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.ui.login.LoginActivity
import de.taz.app.android.ui.main.MainActivity
import kotlinx.android.synthetic.main.fragment_login_confirm_email.*

class RegistrationSuccessfulFragment: LoginBaseFragment(R.layout.fragment_login_registration_successful) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideKeyBoard()

        if (!viewModel.backToArticle || viewModel.isElapsed()) {
            fragment_login_confirm_done.text = getString(
                R.string.fragment_login_success_login_back_coverflow
            )
        }

        fragment_login_confirm_done.setOnClickListener {
            if (viewModel.backToArticle) {
                (activity as? LoginActivity)?.done() ?: activity?.finish()
            } else {
                // go home
                Intent(requireActivity(), MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(this)
                }
            }
        }
    }
}