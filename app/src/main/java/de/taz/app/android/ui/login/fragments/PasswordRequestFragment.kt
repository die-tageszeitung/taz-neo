package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.setError
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionBaseFragment
import kotlinx.android.synthetic.main.fragment_login_forgot_password.*

class PasswordRequestFragment : SubscriptionBaseFragment(R.layout.fragment_login_forgot_password) {

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

        fragment_login_forgot_password_username.setText(
            if (showSubscriptionId) {
                viewModel.subscriptionId?.toString()
            } else {
                viewModel.username ?: viewModel.subscriptionId?.toString()
            }
        )

        if (invalidId) {
            fragment_login_forgot_password_username.setText(viewModel.subscriptionId?.toString())
            fragment_login_forgot_password_username_layout.setError(
                R.string.login_forgot_password_error_invalid_id
            )
        }

        if (invalidMail) {
            fragment_login_forgot_password_username.setText(viewModel.username)
            fragment_login_forgot_password_username_layout.setError(
                R.string.login_email_error_invalid
            )
        }

        fragment_login_forgot_password_button.setOnClickListener {
            ifDoneNext()
        }

        fragment_login_forgot_password_username.setOnEditorActionListener(
            OnEditorActionDoneListener(::ifDoneNext)
        )

    }

    override fun done(): Boolean {
        var done = true
        val username = fragment_login_forgot_password_username.text.toString().trim()
        if (username.isEmpty()) {
            fragment_login_forgot_password_username_layout.error =
                getString(R.string.login_username_error_empty)
            done = false
        } else {
            if (username.toIntOrNull() == null) {
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
                    done = false
                    fragment_login_forgot_password_username_layout.error =
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
        val username = fragment_login_forgot_password_username.text.toString().trim()
        if(username.toIntOrNull() != null) {
            viewModel.requestSubscriptionPassword(username.toInt())
        } else {
            viewModel.requestCredentialsPasswordReset(username)
        }
    }
}