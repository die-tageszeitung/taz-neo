package de.taz.app.android.ui.login.fragments

import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.autofill.HintConstants
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_HTML_FILE_DATA_POLICY
import de.taz.app.android.WEBVIEW_HTML_FILE_REVOCATION
import de.taz.app.android.WEBVIEW_HTML_FILE_TERMS
import de.taz.app.android.databinding.FragmentLoginMissingCredentialsBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.markRequired
import de.taz.app.android.monkey.onClick
import de.taz.app.android.monkey.setError
import de.taz.app.android.ui.WebViewActivity
import de.taz.app.android.ui.login.LoginViewModelState
import de.taz.app.android.ui.login.fragments.subscription.MAX_NAME_LENGTH
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionBaseFragment
import de.taz.app.android.ui.login.passwordCheck.PasswordCheckHelper
import de.taz.app.android.ui.login.passwordCheck.setPasswordHintResponse
import de.taz.app.android.util.hideSoftInputKeyboard
import de.taz.app.android.util.validation.EmailValidator
import kotlinx.coroutines.launch

class CredentialsMissingFragment :
    SubscriptionBaseFragment<FragmentLoginMissingCredentialsBinding>() {

    private val emailValidator = EmailValidator()
    private lateinit var passwordCheckHelper: PasswordCheckHelper

    private var isPasswordValid = false
    private var failed: Boolean = false

    companion object {
        fun create(
            failed: Boolean = false
        ): CredentialsMissingFragment {
            val fragment = CredentialsMissingFragment()
            fragment.failed = failed
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        passwordCheckHelper = PasswordCheckHelper(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding?.apply {
            fragmentLoginMissingCredentialsTermsAndConditions.apply {
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

            fragmentLoginMissingCredentialsEmail.setText(viewModel.username)
            fragmentLoginMissingCredentialsFirstName.setText(viewModel.firstName)
            fragmentLoginMissingCredentialsSurname.setText(viewModel.surName)

            fragmentLoginMissingCredentialsEmailLayout.markRequired()
            fragmentLoginMissingCredentialsPasswordLayout.markRequired()
            fragmentLoginMissingCredentialsPasswordConfirmationLayout.markRequired()
            fragmentLoginMissingCredentialsFirstNameLayout.markRequired()
            fragmentLoginMissingCredentialsSurnameLayout.markRequired()
            fragmentLoginMissingCredentialsTermsAndConditions.markRequired()

            fragmentLoginMissingCredentialsForgotHelp.setOnClickListener {
                showHelpDialog(R.string.fragment_login_missing_credentials_help)
            }

            fragmentLoginMissingCredentialsSwitch.setOnClickListener {
                viewModel.createNewAccount = !viewModel.createNewAccount
                viewModel.status = if (viewModel.createNewAccount) {
                    LoginViewModelState.CREDENTIALS_MISSING_REGISTER
                } else {
                    LoginViewModelState.CREDENTIALS_MISSING_LOGIN
                }
            }

            backButton.setOnClickListener { loginFlowBack() }

            if (viewModel.createNewAccount) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    fragmentLoginMissingCredentialsPassword.setAutofillHints(
                        HintConstants.AUTOFILL_HINT_NEW_PASSWORD
                    )
                }
                fragmentLoginMissingCredentialsForgotPassword.visibility = View.GONE
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    fragmentLoginMissingCredentialsPassword.setAutofillHints(
                        HintConstants.AUTOFILL_HINT_PASSWORD
                    )
                }
                fragmentLoginMissingCredentialsSwitch.text =
                    getString(R.string.fragment_login_missing_credentials_switch_to_registration)

                fragmentLoginMissingCredentialsHeader.text =
                    getString(R.string.fragment_login_missing_credentials_header_login)
                fragmentLoginMissingCredentialsPasswordConfirmationLayout?.visibility =
                    View.GONE
                fragmentLoginMissingCredentialsFirstNameLayout.visibility = View.GONE
                fragmentLoginMissingCredentialsSurnameLayout.visibility = View.GONE
                fragmentLoginMissingCredentialsPassword.imeOptions =
                    EditorInfo.IME_ACTION_DONE
            }

            if (failed) {
                fragmentLoginMissingCredentialsEmailLayout.setError(R.string.login_email_error_recheck)
            }

            fragmentLoginMissingCredentialsLogin.setOnClickListener { ifDoneNext() }

            fragmentLoginMissingCredentialsForgotPassword.setOnClickListener {
                viewModel.requestPasswordReset()
            }

            fragmentLoginMissingCredentialsSurname.setOnEditorActionListener(
                OnEditorActionDoneListener { hideSoftInputKeyboard() }
            )

            fragmentLoginMissingCredentialsPassword.setOnEditorActionListener(
                OnEditorActionDoneListener { hideSoftInputKeyboard() }
            )

            fragmentLoginMissingCredentialsPassword.doAfterTextChanged { checkPassword() }
            fragmentLoginMissingCredentialsEmail.doAfterTextChanged {
                if (fragmentLoginMissingCredentialsPassword.text?.isNotEmpty() == true) {
                    checkPassword()
                }
            }

            fragmentLoginMissingCredentialsFirstName.doAfterTextChanged { text ->
                fragmentLoginMissingCredentialsSurnameLayout.counterMaxLength =
                    (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
            }

            fragmentLoginMissingCredentialsSurname.doAfterTextChanged { text ->
                fragmentLoginMissingCredentialsFirstNameLayout.counterMaxLength =
                    (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
            }

            fragmentLoginMissingCredentialsTermsAndConditions.apply {
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
        }
    }

    override fun done(): Boolean {
        var done = true

        viewBinding?.apply {
            val email = fragmentLoginMissingCredentialsEmail.text?.toString()?.trim()
                ?.lowercase()
            val password = fragmentLoginMissingCredentialsPassword.text?.toString()

            val passwordConfirm =
                fragmentLoginMissingCredentialsPasswordConfirmation.text?.toString()
            val firstName =
                fragmentLoginMissingCredentialsFirstName.text?.toString()?.trim()
            val surname =
                fragmentLoginMissingCredentialsSurname.text?.toString()?.trim()

            if (!isPasswordValid) {
                done = false
            }

            if (password != passwordConfirm && fragmentLoginMissingCredentialsPasswordConfirmationLayout.isVisible) {
                fragmentLoginMissingCredentialsPasswordLayout.setError(R.string.login_password_confirmation_error_match)
                fragmentLoginMissingCredentialsPasswordConfirmationLayout.setError(
                    R.string.login_password_confirmation_error_match
                )
                done = false
            }

            if (firstName.isNullOrEmpty() && fragmentLoginMissingCredentialsFirstNameLayout.isVisible) {
                fragmentLoginMissingCredentialsFirstNameLayout.setError(
                    R.string.login_first_name_error_empty
                )
                done = false
            }
            if (surname.isNullOrEmpty() && fragmentLoginMissingCredentialsSurnameLayout.isVisible) {
                fragmentLoginMissingCredentialsSurnameLayout.setError(
                    R.string.login_surname_error_empty
                )
                done = false
            }

            if (email.isNullOrEmpty()) {
                fragmentLoginMissingCredentialsEmailLayout.setError(
                    R.string.login_email_error_empty
                )
                done = false
            } else {
                if (!emailValidator(email)) {
                    fragmentLoginMissingCredentialsEmailLayout.setError(
                        R.string.login_email_error_invalid
                    )
                    done = false
                }
            }

            if (!fragmentLoginMissingCredentialsTermsAndConditions.isChecked) {
                done = false
                fragmentLoginMissingCredentialsTermsAndConditions.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.error)
                )
            }

            viewModel.username = email
            viewModel.password = password
            viewModel.firstName = firstName
            viewModel.surName = surname
        }
        return done
    }

    override fun next() {
        viewModel.connect()
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
        viewBinding?.apply {
            val mail = fragmentLoginMissingCredentialsEmail.text?.toString() ?: ""
            val password =
                fragmentLoginMissingCredentialsPassword.text?.toString() ?: ""
            viewLifecycleOwner.lifecycleScope.launch {
                val passwordHintResponse = passwordCheckHelper.checkPassword(password, mail)
                isPasswordValid = passwordHintResponse.valid
                fragmentLoginMissingCredentialsPasswordLayout.setPasswordHintResponse(
                    passwordHintResponse
                )
            }
        }
    }

}