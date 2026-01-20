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
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.WebViewActivity
import de.taz.app.android.ui.login.passwordCheck.PasswordCheckHelper
import de.taz.app.android.ui.login.passwordCheck.setPasswordHintResponse
import de.taz.app.android.util.Log
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
    private lateinit var toastHelper: ToastHelper

    private var mailInvalid = false
    private var subscriptionInvalid = false
    private var isPasswordValid = false

    private val log by Log

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
        toastHelper = ToastHelper.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding?.apply {
            fragmentSubscriptionAccountEmail.apply {
                setText(viewModel.username)
            }

            fragmentSubscriptionAccountPassword.apply {
                setText(viewModel.password)
            }

            fragmentSubscriptionAccountPasswordConfirm.apply {
                setText(viewModel.passwordConfirm)
            }

            drawLayout()

            fragmentSubscriptionAccountProceed.setOnClickListener {
                ifDoneNext()
            }

            backButton.setOnClickListener {
                loginFlowBack()
            }

            fragmentSubscriptionAccountForgotPasswordText.setOnClickListener {
                done()
                viewModel.requestPasswordReset()
            }

            fragmentSubscriptionOrderNote.setOnClickListener {
                showHelpDialog(R.string.order_note_text_detail)
                tracker.trackSubscriptionHelpDialog()
            }

            fragmentSubscriptionAccountTermsAndConditions.apply {
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

            fragmentSubscriptionAccountPassword.doAfterTextChanged { checkPassword() }
            fragmentSubscriptionAccountEmail.doAfterTextChanged {
                if (fragmentSubscriptionAccountPassword.text?.isNotEmpty() == true) {
                    checkPassword()
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        tracker.trackSubscriptionAccountLoginFormScreen()
    }

    private fun drawLayout() {
        viewBinding?.apply {

            if (mailInvalid) {
                setEmailError(R.string.login_email_error_invalid)
                fragmentSubscriptionAccountPassword.nextFocusForwardId =
                    R.id.fragment_subscription_account_terms_and_conditions
            }

            if (subscriptionInvalid) {
                setEmailError(R.string.login_email_error_recheck)
            }

            if (viewModel.createNewAccount) {
                fragmentSubscriptionAccountForgotPasswordText.visibility = View.GONE
                fragmentSubscriptionAccountPasswordConfirmLayout.visibility =
                    View.VISIBLE
                fragmentSubscriptionAccountPassword.apply {
                    imeOptions = EditorInfo.IME_ACTION_NEXT
                    nextFocusForwardId = R.id.fragment_subscription_account_password_confirm
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    fragmentSubscriptionAccountEmail.setAutofillHints(HintConstants.AUTOFILL_HINT_EMAIL_ADDRESS)
                    fragmentSubscriptionAccountPassword.setAutofillHints(HintConstants.AUTOFILL_HINT_NEW_PASSWORD)
                }
            } else {
                fragmentSubscriptionAccountForgotPasswordText.visibility = View.VISIBLE
                fragmentSubscriptionAccountPasswordConfirmLayout.visibility = View.GONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    fragmentSubscriptionAccountEmail.setAutofillHints(HintConstants.AUTOFILL_HINT_EMAIL_ADDRESS)
                    fragmentSubscriptionAccountPassword.setAutofillHints(HintConstants.AUTOFILL_HINT_PASSWORD)
                }
            }

            fragmentSubscriptionOrderNote.visibility = View.VISIBLE
            if (fragmentSubscriptionAccountPasswordConfirmLayout.isVisible) {
                fragmentSubscriptionAccountPasswordConfirm
            } else {
                fragmentSubscriptionAccountPassword
            }.apply {
                imeOptions = EditorInfo.IME_ACTION_DONE
                setOnEditorActionListener(
                    OnEditorActionDoneListener { hideSoftInputKeyboard() }
                )
            }

            if (viewModel.validCredentials) {
                fragmentSubscriptionAccountEmailLayout.visibility = View.GONE
                fragmentSubscriptionAccountPasswordLayout.visibility = View.GONE
                fragmentSubscriptionAccountPasswordConfirmLayout.visibility = View.GONE
                fragmentSubscriptionAccountForgotPasswordText.visibility = View.GONE
            }
        }
    }

    override fun next() {
        viewModel.requestSubscription()
    }

    override fun done(): Boolean {
        var done = true

        viewBinding?.apply {

            if (!viewModel.validCredentials) {
                val email =
                    fragmentSubscriptionAccountEmail.text?.toString()?.lowercase()
                        ?.trim()
                if (email.isNullOrBlank() || !emailValidator(email)) {
                    done = false
                    setEmailError(R.string.login_email_error_empty)
                } else {
                    viewModel.username = email
                }

                val password = fragmentSubscriptionAccountPassword.text?.toString()
                if (password.isNullOrBlank()) {
                    done = false
                    setPasswordError(R.string.login_password_error_empty)
                }

                // Check password again without validation hint
                checkPassword(showValidationHint = false)
                if (!isPasswordValid) {
                    done = false
                }

                if (fragmentSubscriptionAccountPasswordConfirmLayout.isVisible) {
                    password?.let { pw ->
                        val passwordConfirmation =
                            fragmentSubscriptionAccountPasswordConfirm.text?.toString()
                        if (pw != passwordConfirmation) {
                            done = false
                            fragmentSubscriptionAccountPasswordConfirmLayout.setError(
                                R.string.login_password_confirmation_error_match
                            )
                        }
                    } ?: run {
                        done = false
                    }
                }
            }


            if (!fragmentSubscriptionAccountTermsAndConditions.isChecked) {
                done = false
                fragmentSubscriptionAccountTermsAndConditions.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.error)
                )
            }

            if (!done) {
                tracker.trackSubscriptionInquiryFormValidationErrorEvent()
                // Scroll to the top:
                scrollView.scrollY = 0
            }

            // Persist the username and (confirm-)password to the view model:
            fragmentSubscriptionAccountEmail.text?.let {
                viewModel.username = it.toString()
            }
            fragmentSubscriptionAccountPassword.text?.let {
                viewModel.password = it.toString()
            }
            fragmentSubscriptionAccountPasswordConfirm.text?.let {
                viewModel.passwordConfirm = it.toString()
            }
        }

        return done
    }

    private fun setEmailError(@StringRes stringRes: Int) {
        viewBinding?.fragmentSubscriptionAccountEmailLayout?.error = context?.getString(stringRes)
    }

    private fun setPasswordError(@StringRes stringRes: Int) {
        viewBinding?.fragmentSubscriptionAccountPasswordLayout?.setError(stringRes)
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

    private fun checkPassword(showValidationHint: Boolean = true) {
        try {
            val mail = viewBinding?.fragmentSubscriptionAccountEmail?.text?.toString() ?: ""
            val password = viewBinding?.fragmentSubscriptionAccountPassword?.text?.toString() ?: ""
            viewLifecycleOwner.lifecycleScope.launch {
                val passwordHintResponse = passwordCheckHelper.checkPassword(password, mail)
                isPasswordValid = passwordHintResponse.valid
                if (showValidationHint) {
                    viewBinding?.fragmentSubscriptionAccountPasswordLayout?.setPasswordHintResponse(
                        passwordHintResponse
                    )
                }
            }
        } catch (npe: NullPointerException) {
            log.warn("Somehow we lost the viewBinding. Exit the fragment and show Toast.")
            toastHelper.showSomethingWentWrongToast()
            viewModel.setDone()
            SentryWrapper.captureException(npe)
        }
    }
}
