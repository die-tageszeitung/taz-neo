package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentLoginForgotPasswordBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.setError
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionBaseFragment
import de.taz.app.android.util.validation.EmailValidator

class PasswordRequestFragment : SubscriptionBaseFragment<FragmentLoginForgotPasswordBinding>() {

    private val emailValidator = EmailValidator()

    private var invalidId: Boolean = false
    private var invalidMail: Boolean = false
    private var showSubscriptionId: Boolean = false

    companion object {
        fun create(
            invalidId: Boolean = false,
            invalidMail: Boolean = false,
            showSubscriptionId: Boolean = false
        ): PasswordRequestFragment {
            val fragment = PasswordRequestFragment()
            fragment.invalidId = invalidId
            fragment.invalidMail = invalidMail
            fragment.showSubscriptionId = showSubscriptionId
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentLoginForgotPasswordUsername.setText(
            if (showSubscriptionId) {
                viewModel.subscriptionId?.toString()
            } else {
                viewModel.username ?: viewModel.subscriptionId?.toString()
            }
        )

        if (invalidId) {
            viewBinding.fragmentLoginForgotPasswordUsername.setText(viewModel.subscriptionId?.toString())
            viewBinding.fragmentLoginForgotPasswordUsernameLayout.setError(
                R.string.login_forgot_password_error_invalid_id
            )
        }

        if (invalidMail) {
            viewBinding.fragmentLoginForgotPasswordUsername.setText(viewModel.username)
            viewBinding.fragmentLoginForgotPasswordUsernameLayout.setError(
                R.string.login_email_error_invalid
            )
        }

        viewBinding.fragmentLoginForgotPasswordButton.setOnClickListener {
            ifDoneNext()
        }

        viewBinding.fragmentLoginForgotPasswordUsername.setOnEditorActionListener(
            OnEditorActionDoneListener(::ifDoneNext)
        )

        viewBinding.fragmentLoginForgotPasswordCancelButton.setOnClickListener {
           back()
        }
    }

    override fun done(): Boolean {
        var done = true
        val username = viewBinding.fragmentLoginForgotPasswordUsername.text.toString().trim()
        if (username.isEmpty()) {
            viewBinding.fragmentLoginForgotPasswordUsernameLayout.error =
                getString(R.string.login_username_error_empty)
            done = false
        } else {
            if (username.toIntOrNull() == null) {
                if (!emailValidator(username)) {
                    done = false
                    viewBinding.fragmentLoginForgotPasswordUsernameLayout.error =
                        getString(R.string.login_email_error_invalid)
                } else {
                    viewModel.username = username
                }
            } else {
                viewModel.subscriptionId = username.toIntOrNull()
            }
        }
        return done
    }

    override fun next() {
        val username = viewBinding.fragmentLoginForgotPasswordUsername.text.toString().trim()
        if(username.toIntOrNull() != null) {
            viewModel.requestSubscriptionPassword(username.toInt())
        } else {
            viewModel.requestCredentialsPasswordReset(username)
        }
    }
}