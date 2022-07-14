package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.databinding.FragmentLoginForgotPasswordEmailSentBinding


class PasswordEmailSentFragment: LoginBaseFragment<FragmentLoginForgotPasswordEmailSentBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentLoginForgotPasswordEmailSentBack.setOnClickListener {
            viewModel.backAfterEmailSent()
        }
    }

}