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
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_HTML_FILE_DATA_POLICY
import de.taz.app.android.WEBVIEW_HTML_FILE_REVOCATION
import de.taz.app.android.WEBVIEW_HTML_FILE_TERMS
import de.taz.app.android.databinding.FragmentSubscriptionAccountBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.onClick
import de.taz.app.android.monkey.setError
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.WebViewActivity
import de.taz.app.android.ui.login.passwordCheck.PasswordCheckHelper
import de.taz.app.android.ui.login.passwordCheck.setPasswordHintResponse
import de.taz.app.android.util.hideSoftInputKeyboard
import de.taz.app.android.util.validation.EmailValidator
import kotlinx.coroutines.launch

class SubscriptionAccountFragment :
    SubscriptionBaseFragment<FragmentSubscriptionAccountBinding>() {

    private val emailValidator = EmailValidator()

    private lateinit var tracker: Tracker
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService
    private lateinit var passwordCheckHelper: PasswordCheckHelper

    private var mailInvalid = false
    private var subscriptionInvalid = false
    private var isPasswordValid = false

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
        tracker = Tracker.getInstance(context.applicationContext)
        fileEntryRepository =  FileEntryRepository.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        passwordCheckHelper = PasswordCheckHelper(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentSubscriptionAccountEmail.apply {
            setText(viewModel.username)
        }

        drawLayout()

        viewBinding.fragmentSubscriptionAccountProceed.setOnClickListener {
            ifDoneNext()
        }

        viewBinding.backButton.setOnClickListener {
            loginFlowBack()
        }

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

        viewBinding.fragmentSubscriptionAccountPassword.doAfterTextChanged { checkPassword() }
        viewBinding.fragmentSubscriptionAccountEmail.doAfterTextChanged {
            if (viewBinding.fragmentSubscriptionAccountPassword.text?.isNotEmpty() == true) {
                checkPassword()
            }
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

        if (subscriptionInvalid) {
            setEmailError(R.string.login_email_error_recheck)
        }

        if (viewModel.createNewAccount) {
            viewBinding.fragmentSubscriptionAccountForgotPasswordText.visibility = View.GONE
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
            viewBinding.fragmentSubscriptionAccountPasswordConfirmLayout.visibility = View.GONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                viewBinding.fragmentSubscriptionAccountEmail.setAutofillHints(HintConstants.AUTOFILL_HINT_EMAIL_ADDRESS)
                viewBinding.fragmentSubscriptionAccountPassword.setAutofillHints(HintConstants.AUTOFILL_HINT_PASSWORD)
            }
        }

        viewBinding.fragmentSubscriptionOrderNote.visibility = View.VISIBLE
        if (viewBinding.fragmentSubscriptionAccountPasswordConfirmLayout.isVisible) {
            viewBinding.fragmentSubscriptionAccountPasswordConfirm
        } else {
            viewBinding.fragmentSubscriptionAccountPassword
        }.apply {
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener(
                OnEditorActionDoneListener { hideSoftInputKeyboard() }
            )
        }

        if (viewModel.validCredentials) {
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
            val email = viewBinding.fragmentSubscriptionAccountEmail.text?.toString()?.lowercase()?.trim()
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

            if (!isPasswordValid) {
                done = false
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
                } ?: run {
                    done = false
                }
            }
        }


        if (!viewBinding.fragmentSubscriptionAccountTermsAndConditions.isChecked) {
            done = false
            viewBinding.fragmentSubscriptionAccountTermsAndConditions.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.error)
            )
        }

        if (!done) {
            tracker.trackSubscriptionInquiryFormValidationErrorEvent()
            // Scroll to the top:
            viewBinding.scrollView.scrollY = 0
        }
        return done
    }

    private fun setEmailError(@StringRes stringRes: Int) {
        viewBinding.fragmentSubscriptionAccountEmailLayout.error = context?.getString(stringRes)
    }

    private fun setPasswordError(@StringRes stringRes: Int) {
        viewBinding.fragmentSubscriptionAccountPasswordLayout.setError(stringRes)
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

    private fun checkPassword() {
        val mail = viewBinding.fragmentSubscriptionAccountEmail.text?.toString() ?: ""
        val password = viewBinding.fragmentSubscriptionAccountPassword.text?.toString() ?: ""
        viewLifecycleOwner.lifecycleScope.launch {
            val passwordHintResponse = passwordCheckHelper.checkPassword(password, mail)
            isPasswordValid = passwordHintResponse.valid
            viewBinding.fragmentSubscriptionAccountPasswordLayout.setPasswordHintResponse(passwordHintResponse)
        }
    }
}
