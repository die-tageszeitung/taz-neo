package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.*
import androidx.annotation.StringRes
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentLoginBinding
import de.taz.app.android.listener.OnEditorActionDoneListener

class LoginFragment : LoginBaseFragment<FragmentLoginBinding>() {

    @StringRes
    private var usernameErrorId: Int? = null

    @StringRes
    private var passwordErrorId: Int? = null

    companion object {
        fun create(
            @StringRes usernameErrorId: Int? = null,
            @StringRes passwordErrorId: Int? = null
        ): LoginFragment {
            return LoginFragment().also {
                it.usernameErrorId = usernameErrorId
                it.passwordErrorId = passwordErrorId
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentLoginUsername.setText(
            viewModel.username ?: viewModel.subscriptionId?.toString()
        )

        viewModel.password?.let {
            viewBinding.fragmentLoginPassword.setText(it)
        }

        usernameErrorId?.let {
            showUserNameError(it)
            usernameErrorId = null
        }
        passwordErrorId?.let {
            showPasswordError(it)
            passwordErrorId = null
        }

        viewBinding.fragmentLoginLoginButton.setOnClickListener {
            login()
        }

        viewBinding.fragmentLoginTrialSubscriptionBoxButton.setOnClickListener {
            viewModel.requestSubscription(viewBinding.fragmentLoginUsername.text.toString().trim())
        }

        viewBinding.fragmentLoginSwitchPrint2digiBoxButton.setOnClickListener {
            viewModel.requestSwitchPrint2Digi()
        }

        viewBinding.fragmentLoginExtendPrintWithDigiBoxButton.setOnClickListener {
            viewModel.requestExtendPrintWithDigi()
        }

        viewBinding.fragmentLoginMissingSubscriptionForgotPassword.setOnClickListener {
            viewModel.requestPasswordReset()
        }

        viewBinding.fragmentLoginForgotHelp.setOnClickListener {
            showHelpDialog(R.string.fragment_login_help)
        }

        viewBinding.fragmentLoginPassword.setOnEditorActionListener(
            OnEditorActionDoneListener(::login)
        )
    }

    private fun login() {
        val username = viewBinding.fragmentLoginUsername.text.toString().trim()
        val password = viewBinding.fragmentLoginPassword.text.toString()
        viewModel.login(username, password)
        hideKeyBoard()
    }

    private fun showPasswordError(passwordErrorId: Int) {
        viewBinding.fragmentLoginPasswordLayout.error = getString(passwordErrorId)
    }

    private fun showUserNameError(@StringRes usernameErrorId: Int) {
        viewBinding.fragmentLoginUsernameLayout.error = getString(usernameErrorId)
    }
}