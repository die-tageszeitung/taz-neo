package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_confirm_email.*

class RegistrationSuccessfulFragment: BaseFragment(R.layout.fragment_login_registration_successful) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_confirm_done.setOnClickListener {
            activity?.finish()
        }
    }
}