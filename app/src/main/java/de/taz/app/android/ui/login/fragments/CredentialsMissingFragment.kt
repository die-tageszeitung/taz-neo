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
        viewBinding.fragmentLoginMissingCredentialsTermsAndConditions.apply {
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

        viewBinding.fragmentLoginMissingCredentialsEmail.apply {
            setText(viewModel.username)
        }
        viewBinding.fragmentLoginMissingCredentialsFirstName.setText(viewModel.firstName)
        viewBinding.fragmentLoginMissingCredentialsSurname.setText(viewModel.surName)

        viewBinding.fragmentLoginMissingCredentialsEmailLayout.markRequired()
        viewBinding.fragmentLoginMissingCredentialsPasswordLayout.markRequired()
        viewBinding.fragmentLoginMissingCredentialsPasswordConfirmationLayout.markRequired()
        viewBinding.fragmentLoginMissingCredentialsFirstNameLayout.markRequired()
        viewBinding.fragmentLoginMissingCredentialsSurnameLayout.markRequired()
        viewBinding.fragmentLoginMissingCredentialsTermsAndConditions.markRequired()

        viewBinding.fragmentLoginMissingCredentialsForgotHelp.setOnClickListener {
            showHelpDialog(R.string.fragment_login_missing_credentials_help)
        }

        viewBinding.fragmentLoginMissingCredentialsSwitch.setOnClickListener {
            viewModel.createNewAccount = !viewModel.createNewAccount
            viewModel.status = if (viewModel.createNewAccount) {
                LoginViewModelState.CREDENTIALS_MISSING_REGISTER
            } else {
                LoginViewModelState.CREDENTIALS_MISSING_LOGIN
            }
        }

        viewBinding.backButton.setOnClickListener { loginFlowBack() }

        if (viewModel.createNewAccount) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                viewBinding.fragmentLoginMissingCredentialsPassword.setAutofillHints(HintConstants.AUTOFILL_HINT_NEW_PASSWORD)
            }
            viewBinding.fragmentLoginMissingCredentialsForgotPassword.visibility = View.GONE
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                viewBinding.fragmentLoginMissingCredentialsPassword.setAutofillHints(HintConstants.AUTOFILL_HINT_PASSWORD)
            }
            viewBinding.fragmentLoginMissingCredentialsSwitch.text =
                getString(R.string.fragment_login_missing_credentials_switch_to_registration)

            viewBinding.fragmentLoginMissingCredentialsHeader.text =
                getString(R.string.fragment_login_missing_credentials_header_login)
            viewBinding.fragmentLoginMissingCredentialsPasswordConfirmationLayout.visibility = View.GONE
            viewBinding.fragmentLoginMissingCredentialsFirstNameLayout.visibility = View.GONE
            viewBinding.fragmentLoginMissingCredentialsSurnameLayout.visibility = View.GONE
            viewBinding.fragmentLoginMissingCredentialsPassword.imeOptions = EditorInfo.IME_ACTION_DONE
        }

        if (failed) {
            viewBinding.fragmentLoginMissingCredentialsEmailLayout.setError(R.string.login_email_error_recheck)
        }

        viewBinding.fragmentLoginMissingCredentialsLogin.setOnClickListener { ifDoneNext() }

        viewBinding.fragmentLoginMissingCredentialsForgotPassword.setOnClickListener {
            viewModel.requestPasswordReset()
        }

        viewBinding.fragmentLoginMissingCredentialsSurname.setOnEditorActionListener(
            OnEditorActionDoneListener { hideSoftInputKeyboard() }
        )

        viewBinding.fragmentLoginMissingCredentialsPassword.setOnEditorActionListener(
            OnEditorActionDoneListener { hideSoftInputKeyboard() }
        )

        viewBinding.fragmentLoginMissingCredentialsPassword.doAfterTextChanged { checkPassword() }
        viewBinding.fragmentLoginMissingCredentialsEmail.doAfterTextChanged {
            if (viewBinding.fragmentLoginMissingCredentialsPassword.text?.isNotEmpty() == true) {
                checkPassword()
            }
        }

        viewBinding.fragmentLoginMissingCredentialsFirstName.doAfterTextChanged { text ->
            viewBinding.fragmentLoginMissingCredentialsSurnameLayout.counterMaxLength =
                (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
        }

        viewBinding.fragmentLoginMissingCredentialsSurname.doAfterTextChanged { text ->
            viewBinding.fragmentLoginMissingCredentialsFirstNameLayout.counterMaxLength =
                (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
        }

        viewBinding.fragmentLoginMissingCredentialsTermsAndConditions.apply {
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

    override fun done(): Boolean {
        val email = viewBinding.fragmentLoginMissingCredentialsEmail.text?.toString()?.trim()?.lowercase()
        val password = viewBinding.fragmentLoginMissingCredentialsPassword.text?.toString()

        val passwordConfirm =
            viewBinding.fragmentLoginMissingCredentialsPasswordConfirmation.text?.toString()
        val firstName = viewBinding.fragmentLoginMissingCredentialsFirstName.text?.toString()?.trim()
        val surname = viewBinding.fragmentLoginMissingCredentialsSurname.text?.toString()?.trim()

        var done = true

        if (!isPasswordValid) {
            done = false
        }

        if (password != passwordConfirm && viewBinding.fragmentLoginMissingCredentialsPasswordConfirmationLayout.isVisible) {
            viewBinding.fragmentLoginMissingCredentialsPasswordLayout.setError(R.string.login_password_confirmation_error_match)
            viewBinding.fragmentLoginMissingCredentialsPasswordConfirmationLayout.setError(
                R.string.login_password_confirmation_error_match
            )
            done = false
        }

        if (firstName.isNullOrEmpty() && viewBinding.fragmentLoginMissingCredentialsFirstNameLayout.isVisible) {
            viewBinding.fragmentLoginMissingCredentialsFirstNameLayout.setError(
                R.string.login_first_name_error_empty
            )
            done = false
        }
        if (surname.isNullOrEmpty() && viewBinding.fragmentLoginMissingCredentialsSurnameLayout.isVisible) {
            viewBinding.fragmentLoginMissingCredentialsSurnameLayout.setError(
                R.string.login_surname_error_empty
            )
            done = false
        }

        if (email.isNullOrEmpty()) {
            viewBinding.fragmentLoginMissingCredentialsEmailLayout.setError(
                R.string.login_email_error_empty
            )
            done = false
        } else {
            if (!emailValidator(email)) {
                viewBinding.fragmentLoginMissingCredentialsEmailLayout.setError(
                    R.string.login_email_error_invalid
                )
                done = false
            }
        }

        if (!viewBinding.fragmentLoginMissingCredentialsTermsAndConditions.isChecked) {
            done = false
            viewBinding.fragmentLoginMissingCredentialsTermsAndConditions.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.error)
            )
        }

        viewModel.username = email
        viewModel.password = password
        viewModel.firstName = firstName
        viewModel.surName = surname

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
        val mail = viewBinding.fragmentLoginMissingCredentialsEmail.text?.toString() ?: ""
        val password = viewBinding.fragmentLoginMissingCredentialsPassword.text?.toString() ?: ""
        viewLifecycleOwner.lifecycleScope.launch {
            val passwordHintResponse = passwordCheckHelper.checkPassword(password, mail)
            isPasswordValid = passwordHintResponse.valid
            viewBinding.fragmentLoginMissingCredentialsPasswordLayout.setPasswordHintResponse(passwordHintResponse)
        }
    }

}