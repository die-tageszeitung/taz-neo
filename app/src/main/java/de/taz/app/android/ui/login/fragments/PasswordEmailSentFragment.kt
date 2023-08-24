package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentLoginForgotPasswordEmailSentBinding


class PasswordEmailSentFragment: LoginBaseFragment<FragmentLoginForgotPasswordEmailSentBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentLoginForgotPasswordEmailSentBack.apply {
            if (viewModel.backToSettingsAfterEmailSent) {
                setText(R.string.fragment_login_success_login_back_settings)
            }
            setOnClickListener {
                viewModel.backAfterEmailSent()
            }
        }
    }

}