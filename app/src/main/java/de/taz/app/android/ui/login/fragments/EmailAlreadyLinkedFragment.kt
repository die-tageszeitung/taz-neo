package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_email_already_taken.*

class EmailAlreadyLinkedFragment : BaseFragment(R.layout.fragment_login_email_already_taken) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_email_already_taken_insert_new.setOnClickListener {
            viewModel.backToLogin()
        }
        fragment_login_email_already_taken_contact_email.setOnClickListener {
            writeEmail()
        }
    }

}