package de.taz.app.android.ui.login.fragments.subscription

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.autofill.HintConstants
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import de.taz.app.android.*
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.markRequired
import de.taz.app.android.monkey.onClick
import de.taz.app.android.monkey.setError
import de.taz.app.android.ui.DataPolicyActivity
import de.taz.app.android.ui.FINISH_ON_CLOSE
import de.taz.app.android.ui.WebViewActivity
import kotlinx.android.synthetic.main.fragment_subscription_account.*
import java.util.regex.Pattern

class SubscriptionAccountFragment :
    SubscriptionBaseFragment(R.layout.fragment_subscription_account) {

    private var mailInvalid = false
    private var subscriptionInvalid = false

    companion object {
        fun createInstance(
            mailInvalid: Boolean = false,
            subscriptionInvalid: Boolean = false
        ): SubscriptionAccountFragment {
            val fragment = SubscriptionAccountFragment()
            fragment.mailInvalid = mailInvalid
            fragment.subscriptionInvalid = subscriptionInvalid
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_subscription_account_email_layout.markRequired()
        fragment_subscription_account_password_layout.markRequired()
        fragment_subscription_account_terms_and_conditions.markRequired()

        fragment_subscription_account_comment.setText(viewModel.comment)
        fragment_subscription_account_email.setText(viewModel.username)
        fragment_subscription_account_comment.setText(viewModel.comment)

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

        fragment_subscription_account_forgot_password_text.setOnClickListener {
            done()
            viewModel.requestPasswordReset()
        }

        fragment_subscription_order_note.setOnClickListener {
            showHelpDialog(R.string.order_note_text_detail)
        }

        fragment_subscription_account_terms_and_conditions.apply {
            val spannableString = SpannableString(text?.toString() ?: "")

            spannableString.onClick(
                resources.getString(R.string.terms_and_conditions_terms),
                ::showTermsAndConditions
            )
            spannableString.onClick(
                resources.getString(R.string.terms_and_conditions_data_policy),
                ::showDataPolicy
            )
            spannableString.onClick(
                resources.getString(R.string.terms_and_conditions_revocation),
                ::showRevocation
            )

            text = spannableString

            movementMethod = LinkMovementMethod.getInstance()
        }

        if (mailInvalid) {
            setEmailError(R.string.login_email_error_invalid)
        }

        if (subscriptionInvalid) {
            setEmailError(R.string.login_email_error_recheck)
        }

    }

    private fun drawLayout() {
        if (mailInvalid) {
            setEmailError(R.string.login_email_error_invalid)
            fragment_subscription_account_password.nextFocusForwardId =
                R.id.fragment_subscription_account_terms_and_conditions
        }

        if (viewModel.createNewAccount) {
            fragment_subscription_account_forgot_password_text.visibility = View.GONE
            fragment_subscription_account_switch_new_account.text =
                getString(R.string.fragment_login_missing_credentials_switch_to_login)
            fragment_subscription_account_password_confirm_layout.visibility = View.VISIBLE
            fragment_subscription_account_password.apply {
                imeOptions = EditorInfo.IME_ACTION_NEXT
                nextFocusForwardId = R.id.fragment_subscription_account_password_confirm
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fragment_subscription_account_email.setAutofillHints(HintConstants.AUTOFILL_HINT_EMAIL_ADDRESS)
                fragment_subscription_account_password.setAutofillHints(HintConstants.AUTOFILL_HINT_NEW_PASSWORD)
            }
        } else {
            fragment_subscription_account_forgot_password_text.visibility = View.VISIBLE
            fragment_subscription_account_switch_new_account.text =
                getString(R.string.fragment_login_missing_credentials_switch_to_registration)
            fragment_subscription_account_password_confirm_layout.visibility = View.GONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fragment_subscription_account_email.setAutofillHints(HintConstants.AUTOFILL_HINT_EMAIL_ADDRESS)
                fragment_subscription_account_password.setAutofillHints(HintConstants.AUTOFILL_HINT_PASSWORD)
            }
        }

        if (viewModel.price == 0) {
            fragment_subscription_account_comment_layout.visibility = View.GONE
            fragment_subscription_order_note.visibility = View.VISIBLE
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
            fragment_subscription_account_proceed.setText(R.string.order_with_costs_button)
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

        if (viewModel.validCredentials) {
            fragment_subscription_account_switch_new_account.visibility = View.GONE
            fragment_subscription_account_email_layout.visibility = View.GONE
            fragment_subscription_account_password_layout.visibility = View.GONE
            fragment_subscription_account_password_confirm_layout.visibility = View.GONE
            fragment_subscription_account_forgot_password_text.visibility = View.GONE
        }
    }

    override fun next() {
        viewModel.requestSubscription()
    }

    override fun done(): Boolean {
        var done = true

        if (!viewModel.validCredentials) {
            val email = fragment_subscription_account_email.text?.toString()
            if (email.isNullOrBlank() || !Pattern.compile(W3C_EMAIL_PATTERN).matcher(email)
                    .matches()
            ) {
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
        }

        viewModel.comment = fragment_subscription_account_comment.text?.toString()

        if (!fragment_subscription_account_terms_and_conditions.isChecked) {
            done = false
            fragment_subscription_account_terms_and_conditions.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.error)
            )
        }
        return done
    }

    private fun setEmailError(@StringRes stringRes: Int) {
        fragment_subscription_account_email_layout.error = context?.getString(stringRes)
    }

    private fun setPasswordError(@StringRes stringRes: Int) {
        fragment_subscription_account_password_layout.error = context?.getString(stringRes)
    }

    private fun showTermsAndConditions() {
        val intent = Intent(activity, WebViewActivity::class.java)
        intent.putExtra(WEBVIEW_HTML_FILE, WEBVIEW_HTML_FILE_TERMS)
        activity?.startActivity(intent)
    }

    private fun showDataPolicy() {
        val intent = Intent(activity, DataPolicyActivity::class.java)
        intent.putExtra(FINISH_ON_CLOSE, true)
        activity?.startActivity(intent)
    }

    private fun showRevocation() {
        val intent = Intent(activity, WebViewActivity::class.java)
        intent.putExtra(WEBVIEW_HTML_FILE, WEBVIEW_HTML_FILE_REVOCATION)
        activity?.startActivity(intent)
    }

}