package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.monkey.markRequired
import kotlinx.android.synthetic.main.fragment_login_missing_names.*

class NamesMissingFragment : LoginBaseFragment(R.layout.fragment_login_missing_names) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_missing_first_name_layout.markRequired()
        fragment_login_missing_surname_layout.markRequired()

        fragment_login_missing_names_login.setOnClickListener {
            getTrialSubscriptionWithFullName()
        }
    }

    private fun getTrialSubscriptionWithFullName() {
        val firstName = fragment_login_missing_first_name.text.toString().trim()
        val surname = fragment_login_missing_surname.text.toString().trim()

        var somethingWrong = false

        if (firstName.isEmpty()) {
            fragment_login_missing_first_name_layout.error = getString(
                R.string.login_first_name_error_empty
            )
            somethingWrong = true
        }
        if (surname.isEmpty()) {
            fragment_login_missing_surname_layout.error = getString(
                R.string.login_surname_error_empty
            )
            somethingWrong = true
        }

        if (somethingWrong) {
            return
        } else {
            viewModel.getTrialSubscriptionForExistingCredentials(
                firstName = firstName,
                surname = surname
            )
        }
    }

}