package de.taz.app.android.ui.login.fragments

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.annotation.RequiresApi
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentLoginForgotPasswordBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.setError
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionBaseFragment
import de.taz.app.android.util.validation.EmailValidator


class PasswordRequestFragment : SubscriptionBaseFragment<FragmentLoginForgotPasswordBinding>() {

    private lateinit var tracker: Tracker

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding?.apply {

            if (showSubscriptionId) {
                fragmentLoginForgotPasswordUsernameLayout.setHint(
                    R.string.login_subscription_hint
                )
                fragmentLoginForgotPasswordUsername.apply {
                    setText(viewModel.subscriptionId?.toString())
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                    setRawInputType(InputType.TYPE_CLASS_NUMBER)
                }
                fragmentLoginForgotPasswordHeader
                    .setText(R.string.fragment_login_forgot_password_for_subscription_header)
            } else {
                fragmentLoginForgotPasswordUsernameLayout.setHint(R.string.login_username_hint)
                fragmentLoginForgotPasswordUsername.apply {
                    setText(viewModel.username ?: viewModel.subscriptionId?.toString())
                }
            }

            if (invalidId) {
                fragmentLoginForgotPasswordUsername.setText(viewModel.subscriptionId?.toString())
                fragmentLoginForgotPasswordUsernameLayout.setError(
                    R.string.login_forgot_password_error_invalid_id
                )
            }

            if (invalidMail) {
                fragmentLoginForgotPasswordUsername.setText(viewModel.username)
                fragmentLoginForgotPasswordUsernameLayout.setError(
                    R.string.login_email_error_invalid
                )
            }

            fragmentLoginForgotPasswordButton.setOnClickListener {
                ifDoneNext()
            }

            fragmentLoginForgotPasswordUsername.setOnEditorActionListener(
                OnEditorActionDoneListener(::ifDoneNext)
            )

            backButton.setOnClickListener {
                loginFlowBack()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.trackForgotPasswordScreen()
    }

    override fun done(): Boolean {
        var done = true
        val username = viewBinding?.fragmentLoginForgotPasswordUsername?.text?.toString()?.trim()?.lowercase()
        if (username.isNullOrEmpty()) {
            viewBinding?.fragmentLoginForgotPasswordUsernameLayout?.error =
                getString(R.string.login_username_error_empty)
            done = false
        } else {
            if (username.toIntOrNull() == null) {
                if (!emailValidator(username)) {
                    done = false
                    viewBinding?.fragmentLoginForgotPasswordUsernameLayout?.error =
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
        val username = viewBinding?.fragmentLoginForgotPasswordUsername?.text?.toString()?.trim()?.lowercase()
        if(username?.toIntOrNull() != null) {
            viewModel.requestSubscriptionPassword(username.toInt())
        } else {
            viewModel.requestCredentialsPasswordReset(username ?: "")
        }
    }
}