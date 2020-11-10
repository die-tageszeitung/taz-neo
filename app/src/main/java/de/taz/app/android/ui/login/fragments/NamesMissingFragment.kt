package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import de.taz.app.android.R
import de.taz.app.android.monkey.markRequired
import de.taz.app.android.ui.login.fragments.subscription.MAX_NAME_LENGTH
import kotlinx.android.synthetic.main.fragment_login_missing_names.*

class NamesMissingFragment : LoginBaseFragment(R.layout.fragment_login_missing_names) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_missing_first_name_layout.markRequired()
        fragment_login_missing_surname_layout.markRequired()

        fragment_login_missing_names_login.setOnClickListener {
            getTrialSubscriptionWithFullName()
        }

        fragment_login_missing_first_name.doAfterTextChanged { text ->
            fragment_login_missing_surname_layout.counterMaxLength =
                (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
        }

        fragment_login_missing_surname.doAfterTextChanged { text ->
            fragment_login_missing_first_name_layout.counterMaxLength =
                (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
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

        viewModel.firstName = firstName
        viewModel.surName = surname

        if (somethingWrong) {
            return
        } else {
            viewModel.getTrialSubscriptionForExistingCredentials(viewModel.status.value)
        }
    }

}