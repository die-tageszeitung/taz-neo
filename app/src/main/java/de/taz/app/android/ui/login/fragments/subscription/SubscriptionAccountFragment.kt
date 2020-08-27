package de.taz.app.android.ui.login.fragments.subscription

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.markRequired
import kotlinx.android.synthetic.main.fragment_subscription_account.*

class SubscriptionAccountFragment : SubscriptionBaseFragment(R.layout.fragment_subscription_account) {

    private var invalidMail = false

    companion object {
        fun createInstance(invalidMail: Boolean = false): SubscriptionAccountFragment {
            val fragment = SubscriptionAccountFragment()
            fragment.invalidMail = invalidMail
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(invalidMail) {
            setEmailError(R.string.login_email_error_invalid)
        }

        fragment_subscription_account_email_layout.markRequired()
        fragment_subscription_account_password_layout.markRequired()
        fragment_subscription_account_terms_and_conditions.markRequired()

        fragment_subscription_account_password.setOnEditorActionListener(
            OnEditorActionDoneListener(::ifDoneNext)
        )

        fragment_subscription_account_proceed.setOnClickListener {
            ifDoneNext()
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

        val acceptedTermsAndCondidtions =
            fragment_subscription_account_terms_and_conditions.isChecked
        if (!acceptedTermsAndCondidtions) {
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