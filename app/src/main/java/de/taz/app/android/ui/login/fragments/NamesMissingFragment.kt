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

        viewBinding.fragmentLoginMissingFirstNameLayout.markRequired()
        viewBinding.fragmentLoginMissingSurnameLayout.markRequired()

        viewBinding.fragmentLoginMissingNamesLogin.setOnClickListener {
            getTrialSubscriptionWithFullName()
        }

        viewBinding.fragmentLoginMissingFirstName.doAfterTextChanged { text ->
            viewBinding.fragmentLoginMissingSurnameLayout.counterMaxLength =
                (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
        }

        viewBinding.fragmentLoginMissingSurname.doAfterTextChanged { text ->
            viewBinding.fragmentLoginMissingFirstNameLayout.counterMaxLength =
                (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
        }
   }

    private fun getTrialSubscriptionWithFullName() {
        val firstName = viewBinding.fragmentLoginMissingFirstName.text.toString().trim()
        val surname = viewBinding.fragmentLoginMissingSurname.text.toString().trim()

        var somethingWrong = false

        if (firstName.isEmpty()) {
            viewBinding.fragmentLoginMissingFirstNameLayout.error = getString(
                R.string.login_first_name_error_empty
            )
            somethingWrong = true
        }
        if (surname.isEmpty()) {
            viewBinding.fragmentLoginMissingSurnameLayout.error = getString(
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