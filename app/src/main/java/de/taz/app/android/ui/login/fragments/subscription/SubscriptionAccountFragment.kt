package de.taz.app.android.ui.login.fragments.subscription

import android.content.Context
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
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_HTML_FILE_DATA_POLICY
import de.taz.app.android.WEBVIEW_HTML_FILE_REVOCATION
import de.taz.app.android.WEBVIEW_HTML_FILE_TERMS
import de.taz.app.android.databinding.FragmentSubscriptionAccountBinding
import de.taz.app.android.getTazApplication
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.onClick
import de.taz.app.android.monkey.setError
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.WebViewActivity
import de.taz.app.android.util.hideSoftInputKeyboard
import de.taz.app.android.util.validation.EmailValidator
import de.taz.app.android.util.validation.PasswordValidator

class SubscriptionAccountFragment :
    SubscriptionBaseFragment<FragmentSubscriptionAccountBinding>() {

    private lateinit var tracker: Tracker

    private val passwordValidator = PasswordValidator()
    private val emailValidator = EmailValidator()

    private var mailInvalid = false
    private var subscriptionInvalid = false

    companion object {
        fun newInstance(
            mailInvalid: Boolean = false,
            subscriptionInvalid: Boolean = false
        ): SubscriptionAccountFragment {
            val fragment = SubscriptionAccountFragment()
            fragment.mailInvalid = mailInvalid
            fragment.subscriptionInvalid = subscriptionInvalid
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = getTazApplication().tracker
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentSubscriptionAccountComment.setText(viewModel.comment)
        viewBinding.fragmentSubscriptionAccountEmail.apply {
            setText(viewModel.username)
        }
        viewBinding.fragmentSubscriptionAccountComment.setText(viewModel.comment)

        drawLayout()

        viewBinding.fragmentSubscriptionAccountSwitchNewAccount.setOnClickListener {
            viewModel.createNewAccount = !viewModel.createNewAccount
            drawLayout()
        }

        viewBinding.fragmentSubscriptionAccountProceed.setOnClickListener {
            ifDoneNext()
        }

        viewBinding.backButton.setOnClickListener {
            back()
        }

        viewBinding.cancelButton.setOnClickListener {
            finish()
        }

        viewBinding.fragmentSubscriptionAccountComment.setOnEditorActionListener(
            OnEditorActionDoneListener{ hideSoftInputKeyboard()}
        )

        viewBinding.fragmentSubscriptionAccountForgotPasswordText.setOnClickListener {
            done()
            viewModel.requestPasswordReset()
        }

        viewBinding.fragmentSubscriptionOrderNote.setOnClickListener {
            showHelpDialog(R.string.order_note_text_detail)
            tracker.trackSubscriptionHelpDialog()
        }

        viewBinding.fragmentSubscriptionAccountTermsAndConditions.apply {
            val spannableString = SpannableString(text?.toString() ?: "")

            spannableString.onClick(resources.getString(R.string.terms_and_conditions_terms)) {
                showTermsAndConditions()
            }
            spannableString.onClick(resources.getString(R.string.terms_and_conditions_data_policy)) {
                showDataPolicy()
            }
            spannableString.onClick(resources.getString(R.string.terms_and_conditions_revocation)) {
                showRevocation()
            }

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

    override fun onResume() {
        super.onResume()
        tracker.trackSubscriptionAccountLoginFormScreen()
    }

    private fun drawLayout() {
        if (mailInvalid) {
            setEmailError(R.string.login_email_error_invalid)
            viewBinding.fragmentSubscriptionAccountPassword.nextFocusForwardId =
                R.id.fragment_subscription_account_terms_and_conditions
        }

        if (viewModel.createNewAccount) {
            viewBinding.fragmentSubscriptionAccountForgotPasswordText.visibility = View.GONE
            viewBinding.fragmentSubscriptionAccountSwitchNewAccount.text =
                getString(R.string.fragment_login_missing_credentials_switch_to_login)
            viewBinding.fragmentSubscriptionAccountPasswordConfirmLayout.visibility = View.VISIBLE
            viewBinding.fragmentSubscriptionAccountPassword.apply {
                imeOptions = EditorInfo.IME_ACTION_NEXT
                nextFocusForwardId = R.id.fragment_subscription_account_password_confirm
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                viewBinding.fragmentSubscriptionAccountEmail.setAutofillHints(HintConstants.AUTOFILL_HINT_EMAIL_ADDRESS)
                viewBinding.fragmentSubscriptionAccountPassword.setAutofillHints(HintConstants.AUTOFILL_HINT_NEW_PASSWORD)
            }
        } else {
            viewBinding.fragmentSubscriptionAccountForgotPasswordText.visibility = View.VISIBLE
            viewBinding.fragmentSubscriptionAccountSwitchNewAccount.text =
                getString(R.string.fragment_login_missing_credentials_switch_to_registration)
            viewBinding.fragmentSubscriptionAccountPasswordConfirmLayout.visibility = View.GONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                viewBinding.fragmentSubscriptionAccountEmail.setAutofillHints(HintConstants.AUTOFILL_HINT_EMAIL_ADDRESS)
                viewBinding.fragmentSubscriptionAccountPassword.setAutofillHints(HintConstants.AUTOFILL_HINT_PASSWORD)
            }
        }

        if (viewModel.price == 0) {
            viewBinding.fragmentSubscriptionAccountCommentLayout.visibility = View.GONE
            viewBinding.fragmentSubscriptionOrderNote.visibility = View.VISIBLE
            if (viewBinding.fragmentSubscriptionAccountPasswordConfirmLayout.isVisible) {
                viewBinding.fragmentSubscriptionAccountPasswordConfirm
            } else {
                viewBinding.fragmentSubscriptionAccountPassword
            }.apply {
                imeOptions = EditorInfo.IME_ACTION_DONE
                setOnEditorActionListener(
                    OnEditorActionDoneListener{ hideSoftInputKeyboard() }
                )
            }
        } else {
            viewBinding.fragmentSubscriptionAccountProceed.setText(R.string.order_with_costs_button)
            viewBinding.fragmentSubscriptionAccountCommentLayout.visibility = View.VISIBLE
            if (viewBinding.fragmentSubscriptionAccountPasswordConfirmLayout.isVisible) {
                viewBinding.fragmentSubscriptionAccountPasswordConfirm
            } else {
                viewBinding.fragmentSubscriptionAccountPassword
            }.apply {
                imeOptions = EditorInfo.IME_ACTION_NEXT
                nextFocusForwardId = R.id.fragment_subscription_account_comment
            }
        }

        if (viewModel.validCredentials) {
            viewBinding.fragmentSubscriptionAccountSwitchNewAccount.visibility = View.GONE
            viewBinding.fragmentSubscriptionAccountEmailLayout.visibility = View.GONE
            viewBinding.fragmentSubscriptionAccountPasswordLayout.visibility = View.GONE
            viewBinding.fragmentSubscriptionAccountPasswordConfirmLayout.visibility = View.GONE
            viewBinding.fragmentSubscriptionAccountForgotPasswordText.visibility = View.GONE
        }
    }

    override fun next() {
        viewModel.requestSubscription()
    }

    override fun done(): Boolean {
        var done = true

        if (!viewModel.validCredentials) {
            val email = viewBinding.fragmentSubscriptionAccountEmail.text?.toString()?.lowercase()
            if (email.isNullOrBlank() || !emailValidator(email)) {
                done = false
                setEmailError(R.string.login_email_error_empty)
            } else {
                viewModel.username = email
            }

            val password = viewBinding.fragmentSubscriptionAccountPassword.text?.toString()
            if (password.isNullOrBlank()) {
                done = false
                setPasswordError(R.string.login_password_error_empty)
            } else {
                viewModel.password = password
            }

            if (viewBinding.fragmentSubscriptionAccountPasswordConfirmLayout.isVisible) {
                password?.let { pw ->
                    val passwordConfirmation =
                        viewBinding.fragmentSubscriptionAccountPasswordConfirm.text?.toString()
                    if (pw != passwordConfirmation) {
                        done = false
                        viewBinding.fragmentSubscriptionAccountPasswordConfirmLayout.setError(
                            R.string.login_password_confirmation_error_match
                        )
                    }
                    if (!passwordValidator(pw)) {
                        done = false
                        viewBinding.fragmentSubscriptionAccountPasswordLayout.setError(
                            R.string.login_password_regex_error
                        )
                    }
                } ?: run {
                    done = false
                }
            }
        }

        viewModel.comment = viewBinding.fragmentSubscriptionAccountComment.text?.toString()

        if (!viewBinding.fragmentSubscriptionAccountTermsAndConditions.isChecked) {
            done = false
            viewBinding.fragmentSubscriptionAccountTermsAndConditions.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.error)
            )
        }

        if (!done) {
            tracker.trackSubscriptionInquiryFormValidationErrorEvent()
        }
        return done
    }

    private fun setEmailError(@StringRes stringRes: Int) {
        viewBinding.fragmentSubscriptionAccountEmailLayout.error = context?.getString(stringRes)
    }

    private fun setPasswordError(@StringRes stringRes: Int) {
        viewBinding.fragmentSubscriptionAccountPasswordLayout.error = context?.getString(stringRes)
    }

    private fun showTermsAndConditions() {
        activity?.apply {
            val intent = WebViewActivity.newIntent(this, WEBVIEW_HTML_FILE_TERMS)
            startActivity(intent)
        }
    }

    private fun showDataPolicy() {
        activity?.apply {
            val intent = WebViewActivity.newIntent(this, WEBVIEW_HTML_FILE_DATA_POLICY)
            startActivity(intent)
        }
    }

    private fun showRevocation() {
        activity?.apply {
            val intent = WebViewActivity.newIntent(this, WEBVIEW_HTML_FILE_REVOCATION)
            startActivity(intent)
        }
    }

}