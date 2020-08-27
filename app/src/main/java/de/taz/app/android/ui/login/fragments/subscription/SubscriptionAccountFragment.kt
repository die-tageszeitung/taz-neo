package de.taz.app.android.ui.login.fragments.subscription

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.markRequired
import de.taz.app.android.monkey.setError
import kotlinx.android.synthetic.main.fragment_subscription_account.*

class SubscriptionAccountFragment :
    SubscriptionBaseFragment(R.layout.fragment_subscription_account) {

    private var invalidMail = false


    companion object {
        fun createInstance(
            invalidMail: Boolean = false
        ): SubscriptionAccountFragment {
            val fragment = SubscriptionAccountFragment()
            fragment.invalidMail = invalidMail
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_subscription_account_email_layout.markRequired()
        fragment_subscription_account_password_layout.markRequired()
        fragment_subscription_account_terms_and_conditions.markRequired()

        drawLayout()

        fragment_subscription_account_switch_new_account.setOnClickListener {
            viewModel.createNewAccount = !viewModel.createNewAccount
            drawLayout()
        }

        fragment_subscription_account_proceed.setOnClickListener {
            ifDoneNext()
        }

        fragment_subscription_account_comment.setOnEditorActionListener(
            OnEditorActionDoneListener(::hideKeyBoard)
        )
    }

    private fun drawLayout() {
        if (invalidMail) {
            setEmailError(R.string.login_email_error_invalid)
            fragment_subscription_account_password.nextFocusForwardId =
                R.id.fragment_subscription_account_terms_and_conditions
        }

        if (viewModel.createNewAccount) {
            fragment_subscription_account_switch_new_account.text =
                getString(R.string.fragment_login_missing_credentials_switch_to_login)
            fragment_subscription_account_password_confirm_layout.visibility = View.VISIBLE
            fragment_subscription_account_password.apply {
                imeOptions = EditorInfo.IME_ACTION_NEXT
                nextFocusForwardId = R.id.fragment_subscription_account_password_confirm
            }
        } else {
            fragment_subscription_account_switch_new_account.text =
                getString(R.string.fragment_login_missing_credentials_switch_to_registration)
            fragment_subscription_account_password_confirm_layout.visibility = View.GONE
        }

        if (viewModel.price == 0) {
            fragment_subscription_account_comment_layout.visibility = View.GONE
            if (fragment_subscription_account_password_confirm_layout.isVisible) {
                fragment_subscription_account_password_confirm
            } else {
                fragment_subscription_account_password
            }.apply {
                imeOptions = EditorInfo.IME_ACTION_DONE
                setOnEditorActionListener(
                    OnEditorActionDoneListener(this@SubscriptionAccountFragment::hideKeyBoard)
                )
            }
        } else {
            fragment_subscription_account_comment_layout.visibility = View.VISIBLE
            if (fragment_subscription_account_password_confirm_layout.isVisible) {
                fragment_subscription_account_password_confirm
            } else {
                fragment_subscription_account_password
            }.apply {
                imeOptions = EditorInfo.IME_ACTION_NEXT
                nextFocusForwardId = R.id.fragment_subscription_account_comment
            }
        }
    }

    override fun next() {
        TODO()
    }

    override fun done(): Boolean {
        var done = true

        val email = fragment_subscription_account_email.text?.toString()
        if (email.isNullOrBlank()) {
            done = false
            setEmailError(R.string.login_email_error_empty)
        } else {
            viewModel.username = email
        }

        val password = fragment_subscription_account_password.text?.toString()
        if (password.isNullOrBlank()) {
            done = false
            setPasswordError(R.string.login_password_error_empty)
        } else {
            viewModel.password = password
        }

        if (fragment_subscription_account_password_confirm_layout.isVisible) {
            val passwordConfirmation =
                fragment_subscription_account_password_confirm.text?.toString()
            if (password != passwordConfirmation) {
                done = false
                fragment_subscription_account_password_confirm_layout.setError(
                    R.string.login_password_confirmation_error_match
                )
            }
        }

        viewModel.comment = fragment_subscription_account_comment.text?.toString()

        val acceptedTermsAndConditions =
            fragment_subscription_account_terms_and_conditions.isChecked
        if (!acceptedTermsAndConditions) {
            done = false
            fragment_subscription_account_terms_and_conditions.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.tazRed)
            )
        }
        return done
    }

    fun setEmailError(@StringRes stringRes: Int) {
        fragment_subscription_account_email_layout.error = context?.getString(stringRes)
    }

    fun setPasswordError(@StringRes stringRes: Int) {
        fragment_subscription_account_password_layout.error = context?.getString(stringRes)
    }
}