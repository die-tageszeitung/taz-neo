package de.taz.app.android.ui.login.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentLoginBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.hideSoftInputKeyboard

class LoginFragment : LoginBaseFragment<FragmentLoginBinding>() {

    private lateinit var tracker: Tracker

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentLoginUsername.apply {
            setText(viewModel.username ?: viewModel.subscriptionId?.toString())
        }

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

        viewBinding.cancelButton.setOnClickListener {
            finish()
        }

        viewBinding.fragmentLoginTrialSubscriptionBoxButton.setOnClickListener {
            viewModel.requestSubscription(viewBinding.fragmentLoginUsername.text.toString().trim().lowercase())
        }

        viewBinding.fragmentLoginSwitchPrint2digiBoxButton.setOnClickListener {
            viewModel.requestSwitchPrint2Digi()
        }

        viewBinding.fragmentLoginExtendPrintWithDigiBoxButton.setOnClickListener {
            viewModel.requestExtendPrintWithDigi()
        }

        if (BuildConfig.IS_LMD) {
            viewBinding.fragmentLoginMissingSubscriptionForgotPassword.visibility = View.GONE
        } else {
            viewBinding.fragmentLoginMissingSubscriptionForgotPassword.setOnClickListener {
                viewModel.requestPasswordReset()
            }
        }

        viewBinding.fragmentLoginForgottenHelp.setOnClickListener {
            showHelpDialog(R.string.fragment_login_help)
            tracker.trackLoginHelpDialog()
        }

        viewBinding.fragmentLoginPassword.setOnEditorActionListener(
            OnEditorActionDoneListener {
                login()
            }
        )

        if (BuildConfig.IS_LMD) {
            hideSubscriptionBoxes()
        }
    }

    private fun login() {
        val username = viewBinding.fragmentLoginUsername.text.toString().trim().lowercase()
        val password = viewBinding.fragmentLoginPassword.text.toString()
        viewModel.login(username, password)
        hideSoftInputKeyboard()
    }

    private fun showPasswordError(passwordErrorId: Int) {
        viewBinding.fragmentLoginPasswordLayout.error = getString(passwordErrorId)
    }

    private fun showUserNameError(@StringRes usernameErrorId: Int) {
        viewBinding.fragmentLoginUsernameLayout.error = getString(usernameErrorId)
    }

    private fun hideSubscriptionBoxes() {
        viewBinding.fragmentLoginSeparatorLine.visibility = View.GONE
        viewBinding.fragmentLoginTrialSubscriptionBox.visibility = View.GONE
        viewBinding.fragmentLoginSwitchPrint2digiBox.visibility = View.GONE
        viewBinding.fragmentLoginExtendPrintWithDigiBox.visibility = View.GONE
    }
}