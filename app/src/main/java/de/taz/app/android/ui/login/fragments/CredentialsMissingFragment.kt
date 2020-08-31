package de.taz.app.android.ui.login.fragments

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_HTML_FILE
import de.taz.app.android.WEBVIEW_HTML_FILE_REVOCATION
import de.taz.app.android.WEBVIEW_HTML_FILE_TERMS
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.markRequired
import de.taz.app.android.monkey.onClick
import de.taz.app.android.monkey.setError
import de.taz.app.android.ui.DataPolicyActivity
import de.taz.app.android.ui.FINISH_ON_CLOSE
import de.taz.app.android.ui.WebViewActivity
import de.taz.app.android.ui.login.LoginViewModelState
import de.taz.app.android.ui.login.fragments.subscription.MAX_NAME_LENGTH
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionBaseFragment
import kotlinx.android.synthetic.main.fragment_login_missing_credentials.*

class CredentialsMissingFragment :
    SubscriptionBaseFragment(R.layout.fragment_login_missing_credentials) {

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragment_login_missing_credentials_terms_and_conditions.apply {
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

        fragment_login_missing_credentials_email.setText(viewModel.username)
        fragment_login_missing_credentials_first_name.setText(viewModel.firstName)
        fragment_login_missing_credentials_surname.setText(viewModel.surName)

        fragment_login_missing_credentials_email_layout.markRequired()
        fragment_login_missing_credentials_password_layout.markRequired()
        fragment_login_missing_credentials_password_confirmation_layout.markRequired()
        fragment_login_missing_credentials_first_name_layout.markRequired()
        fragment_login_missing_credentials_surname_layout.markRequired()
        fragment_login_missing_credentials_terms_and_conditions.markRequired()

        fragment_login_missing_credentials_switch.setOnClickListener {
            viewModel.createNewAccount = !viewModel.createNewAccount
            viewModel.status.postValue(
                if (viewModel.createNewAccount) {
                    LoginViewModelState.CREDENTIALS_MISSING_REGISTER
                } else {
                    LoginViewModelState.CREDENTIALS_MISSING_LOGIN
                }
            )
        }

        if (viewModel.createNewAccount) {
            fragment_login_missing_credentials_forgot_password.visibility = View.GONE
        } else {
            fragment_login_missing_credentials_switch.text =
                getString(R.string.fragment_login_missing_credentials_switch_to_registration)

            fragment_login_missing_credentials_header.text =
                getString(R.string.fragment_login_missing_credentials_header_login)
            fragment_login_missing_credentials_password_confirmation_layout.visibility = View.GONE
            fragment_login_missing_credentials_first_name_layout.visibility = View.GONE
            fragment_login_missing_credentials_surname_layout.visibility = View.GONE
            fragment_login_missing_credentials_password.imeOptions = EditorInfo.IME_ACTION_DONE
        }

        if (failed) {
            fragment_login_missing_credentials_email_layout.setError(R.string.login_email_error_recheck)
        }

        fragment_login_missing_credentials_login.setOnClickListener { ifDoneNext() }

        fragment_login_missing_credentials_forgot_password?.setOnClickListener {
            viewModel.requestPasswordReset()
        }

        fragment_login_missing_credentials_surname.setOnEditorActionListener(
            OnEditorActionDoneListener(this@CredentialsMissingFragment::hideKeyBoard)
        )

        fragment_login_missing_credentials_password.setOnEditorActionListener(
            OnEditorActionDoneListener(this@CredentialsMissingFragment::hideKeyBoard)
        )

        fragment_login_missing_credentials_first_name.doAfterTextChanged { text ->
            fragment_login_missing_credentials_surname_layout.counterMaxLength =
                (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
        }

        fragment_login_missing_credentials_surname.doAfterTextChanged { text ->
            fragment_login_missing_credentials_first_name_layout.counterMaxLength =
                (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
        }

        fragment_login_missing_credentials_terms_and_conditions.apply {
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
        val email = fragment_login_missing_credentials_email.text.toString().trim()
        val password = fragment_login_missing_credentials_password.text.toString()

        val passwordConfirm =
            fragment_login_missing_credentials_password_confirmation.text.toString()
        val firstName = fragment_login_missing_credentials_first_name.text.toString().trim()
        val surname = fragment_login_missing_credentials_surname.text.toString().trim()

        var done = true

        if (password != passwordConfirm && fragment_login_missing_credentials_password_confirmation_layout.isVisible) {
            fragment_login_missing_credentials_password_layout.setError(R.string.login_password_confirmation_error_match)
            fragment_login_missing_credentials_password_confirmation_layout.setError(
                R.string.login_password_confirmation_error_match
            )
            done = false
        }
        if (firstName.isEmpty() && fragment_login_missing_credentials_first_name_layout.isVisible) {
            fragment_login_missing_credentials_first_name_layout.setError(
                R.string.login_first_name_error_empty
            )
            done = false
        }
        if (surname.isEmpty() && fragment_login_missing_credentials_surname_layout.isVisible) {
            fragment_login_missing_credentials_surname_layout.setError(
                R.string.login_surname_error_empty
            )
            done = false
        }

        if (email.isEmpty()) {
            fragment_login_missing_credentials_email_layout.setError(
                R.string.login_email_error_empty
            )
            done = false
        } else {
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                fragment_login_missing_credentials_email_layout.setError(
                    R.string.login_email_error_invalid
                )
                done = false
            }
        }

        if (password.isEmpty()) {
            fragment_login_missing_credentials_password_layout.setError(
                R.string.login_password_error_empty
            )
            done = false
        }

        if (!fragment_login_missing_credentials_terms_and_conditions.isChecked) {
            done = false
            fragment_login_missing_credentials_terms_and_conditions.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.tazRed)
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