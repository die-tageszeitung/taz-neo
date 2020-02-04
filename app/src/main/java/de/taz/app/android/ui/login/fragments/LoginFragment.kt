package de.taz.app.android.ui.login.fragments

import android.app.Activity
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.taz.app.android.R
import de.taz.app.android.ui.login.LoginViewModel
import kotlinx.android.synthetic.main.fragment_login.*

class LoginFragment : Fragment(R.layout.fragment_login) {

    private var username: String? = null
    private var password: String? = null

    private val viewModel = activityViewModels<LoginViewModel>()

    @StringRes
    private var usernameErrorId: Int? = null
    @StringRes
    private var passwordErrorId: Int? = null

    companion object {
        fun create(
            username: String? = null,
            password: String? = null,
            @StringRes usernameErrorId: Int? = null,
            @StringRes passwordErrorId: Int? = null
        ): LoginFragment {
            return LoginFragment().also {
                it.username = username
                it.password = password
                it.usernameErrorId = usernameErrorId
                it.passwordErrorId = passwordErrorId
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        username?.let {
            fragment_login_username_text.setText(it)
        }

        password?.let {
            fragment_login_password_text.setText(it)
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

        fragment_login_password_text.setOnEditorActionListener(object : TextView.OnEditorActionListener {
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
        username = fragment_login_username_text.text.toString()
        password = fragment_login_password_text.text.toString()
        viewModel.value.login(username, password)
        hideKeyBoard()
    }

    private fun hideKeyBoard() {
        activity?.apply {
            (getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)?.apply {
                val view = activity?.currentFocus ?: View(activity)
                hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    private fun showPasswordError(passwordErrorId: Int) {
        fragment_login_password.error = getString(passwordErrorId)
    }

    private fun showUserNameError(@StringRes usernameErrorId: Int) {
        fragment_login_username.error = getString(usernameErrorId)
    }
}