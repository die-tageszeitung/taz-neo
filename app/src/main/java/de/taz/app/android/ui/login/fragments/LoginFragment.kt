package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.*
import androidx.annotation.StringRes
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import kotlinx.android.synthetic.main.fragment_login.*

class LoginFragment : LoginBaseFragment(R.layout.fragment_login) {

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

        fragment_login_username.setText(viewModel.username ?: viewModel.subscriptionId?.toString())

        viewModel.password?.let {
            fragment_login_password.setText(it)
        }

        usernameErrorId?.let {
            showUserNameError(it)
            usernameErrorId = null
        }
        passwordErrorId?.let {
            showPasswordError(it)
            passwordErrorId = null
        }

        fragment_login_login_button.setOnClickListener {
            login()
        }

        fragment_login_register_button.setOnClickListener {
            viewModel.requestSubscription(fragment_login_username.text.toString().trim())
        }

        fragment_login_missing_subscription_forgot_password.setOnClickListener {
            viewModel.requestPasswordReset()
        }

        fragment_login_forgot_help.setOnClickListener {
            showHelpDialog(R.string.fragment_login_help)
        }

        fragment_login_password.setOnEditorActionListener(
            OnEditorActionDoneListener(::login)
        )

    }

    private fun login() {
        val username = fragment_login_username.text.toString().trim()
        val password = fragment_login_password.text.toString()
        viewModel.login(username, password)
        hideKeyBoard()
    }

    private fun showPasswordError(passwordErrorId: Int) {
        fragment_login_password_layout.error = getString(passwordErrorId)
    }

    private fun showUserNameError(@StringRes usernameErrorId: Int) {
        fragment_login_username_layout.error = getString(usernameErrorId)
    }
}