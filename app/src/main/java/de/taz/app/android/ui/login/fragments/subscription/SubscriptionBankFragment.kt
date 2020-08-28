package de.taz.app.android.ui.login.fragments.subscription

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.markRequired
import de.taz.app.android.monkey.setError
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.android.synthetic.main.fragment_subscription_bank.*

class SubscriptionBankFragment : SubscriptionBaseFragment(R.layout.fragment_subscription_bank) {

    var accountHolderInvalid: Boolean = false
    var ibanEmpty: Boolean = false
    var ibanInvalid: Boolean = false
    var ibanNoSepa: Boolean = false

    companion object {
        fun createInstance(
            accountHolderInvalid: Boolean = false,
            ibanEmpty: Boolean = false,
            ibanInvalid: Boolean = false,
            ibanNoSepa: Boolean = false
        ): SubscriptionBankFragment {
            val fragment = SubscriptionBankFragment()
            fragment.accountHolderInvalid = accountHolderInvalid
            fragment.ibanEmpty = ibanEmpty
            fragment.ibanInvalid = ibanInvalid
            fragment.ibanNoSepa = ibanNoSepa
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_subscription_bank_iban_layout.markRequired()

        fragment_subscription_bank_account_holder.setOnEditorActionListener(
            OnEditorActionDoneListener(::ifDoneNext)
        )

        viewModel.apply {
            iban?.let { fragment_subscription_bank_iban.setText(it) }
            accountHolder?.let { fragment_subscription_bank_account_holder.setText(it) }
        }

        fragment_subscription_bank_proceed.setOnClickListener { ifDoneNext() }

        if(accountHolderInvalid) {
            setAccountHolderError(R.string.subscription_field_invalid)
        }

        if(ibanEmpty) {
            setIbanError(R.string.iban_error_empty)
        }

        if (ibanInvalid) {
            setIbanError(R.string.iban_error_invalid)
        }

        if(ibanNoSepa) {
            setIbanError(R.string.iban_error_no_sepa)
        }
    }

    override fun done(): Boolean {
        var done = true
        if (fragment_subscription_bank_iban.text.isNullOrBlank()) {
            done = false
            setIbanError(R.string.iban_error_empty)
            if (!fragment_subscription_bank_account_holder.text.isNullOrBlank()) {
                viewModel.accountHolder =
                    fragment_subscription_bank_account_holder.text.toString()
            }
        }
        viewModel.iban = fragment_subscription_bank_iban.text.toString()
        viewModel.accountHolder = fragment_subscription_bank_account_holder.text.toString()
        return done
    }

    override fun next() {
        viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_ACCOUNT)
    }

    fun setIbanError(@StringRes stringRes: Int) {
        fragment_subscription_bank_iban_layout.setError(stringRes)
    }

    fun setAccountHolderError(@StringRes stringRes: Int) {
        fragment_subscription_bank_iban_layout.setError(stringRes)
    }
}