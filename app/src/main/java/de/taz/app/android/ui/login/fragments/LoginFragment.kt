package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.StringRes
import de.taz.app.android.R
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.android.synthetic.main.fragment_login.*

class LoginFragment : BaseFragment(R.layout.fragment_login) {

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

        viewModel.username?.let {
            fragment_login_username.setText(it)
        }

        viewModel.password?.let {
            fragment_login_password.setText(it)
        }

        var showForgotPassword = false
        usernameErrorId?.let {
            showUserNameError(it)
            usernameErrorId = null
            showForgotPassword = true
        }
        passwordErrorId?.let {
            showPasswordError(it)
            passwordErrorId = null
            showForgotPassword = true
        }

        if (showForgotPassword && viewModel.username?.contains("@") == true) {
            fragment_login_forgot_password_text.visibility = View.VISIBLE
            fragment_login_forgot_password_button.visibility = View.VISIBLE
        }

        fragment_login_login_button.setOnClickListener {
            login()
        }

        fragment_login_register_button.setOnClickListener {
            // TODO do not set status here?!
            viewModel.username = fragment_login_username.text.toString()
            viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_REQUEST)
        }

        fragment_login_forgot_password_button.setOnClickListener {
            viewModel.resetCredentialsPassword(fragment_login_username.text.toString())
        }

        fragment_login_password.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    login()
                    return true
                }
                return false
            }
        })
    }

    private fun login() {
        val username = fragment_login_username.text.toString()
        val password = fragment_login_password.text.toString()
        viewModel.login(username, password)
        hideKeyBoard()
    }

    private fun showPasswordError(passwordErrorId: Int) {
        fragment_login_password.error = getString(passwordErrorId)
    }

    private fun showUserNameError(@StringRes usernameErrorId: Int) {
        fragment_login_username.error = getString(usernameErrorId)
    }
}