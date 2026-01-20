package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentLoginMissingNamesBinding
import de.taz.app.android.monkey.markRequired
import de.taz.app.android.ui.login.fragments.subscription.MAX_NAME_LENGTH

class NamesMissingFragment : LoginBaseFragment<FragmentLoginMissingNamesBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding?.apply {
            fragmentLoginMissingFirstNameLayout.markRequired()
            fragmentLoginMissingSurnameLayout.markRequired()

            fragmentLoginMissingNamesLogin.setOnClickListener {
                getTrialSubscriptionWithFullName()
            }

            fragmentLoginMissingFirstName.doAfterTextChanged { text ->
                fragmentLoginMissingSurnameLayout.counterMaxLength =
                    (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
            }

            fragmentLoginMissingSurname.doAfterTextChanged { text ->
                fragmentLoginMissingFirstNameLayout.counterMaxLength =
                    (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
            }
        }
   }

    private fun getTrialSubscriptionWithFullName() {
        val firstName = viewBinding?.fragmentLoginMissingFirstName?.text?.toString()?.trim()
        val surname = viewBinding?.fragmentLoginMissingSurname?.text?.toString()?.trim()

        var somethingWrong = false

        if (firstName.isNullOrEmpty()) {
            viewBinding?.fragmentLoginMissingFirstNameLayout?.error = getString(
                R.string.login_first_name_error_empty
            )
            somethingWrong = true
        }
        if (surname.isNullOrEmpty()) {
            viewBinding?.fragmentLoginMissingSurnameLayout?.error = getString(
                R.string.login_surname_error_empty
            )
            somethingWrong = true
        }

        viewModel.firstName = firstName
        viewModel.surName = surname

        if (somethingWrong) {
            return
        } else {
            viewModel.getTrialSubscriptionForExistingCredentials(viewModel.status)
        }
    }

}