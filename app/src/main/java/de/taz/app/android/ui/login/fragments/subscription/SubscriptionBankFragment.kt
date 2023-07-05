package de.taz.app.android.ui.login.fragments.subscription

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentSubscriptionBankBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.markRequired
import de.taz.app.android.monkey.setError
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.login.LoginViewModelState
import nl.garvelink.iban.IBAN

class SubscriptionBankFragment : SubscriptionBaseFragment<FragmentSubscriptionBankBinding>() {

    private lateinit var tracker: Tracker

    var accountHolderInvalid: Boolean = false
    var ibanEmpty: Boolean = false
    var ibanInvalid: Boolean = false
    var ibanNoSepa: Boolean = false

    companion object {
        fun newInstance(
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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentSubscriptionBankIbanLayout.markRequired()

        viewBinding.fragmentSubscriptionBankAccountHolder.setOnEditorActionListener(
            OnEditorActionDoneListener(::ifDoneNext)
        )

        viewModel.apply {
            iban?.let { viewBinding.fragmentSubscriptionBankIban.setText(it) }
            accountHolder?.let { viewBinding.fragmentSubscriptionBankAccountHolder.setText(it) }
        }

        viewBinding.fragmentSubscriptionBankProceed.setOnClickListener {
            ifDoneNext()
        }

        viewBinding.backButton.setOnClickListener {
            back()
        }

        if (accountHolderInvalid) {
            setAccountHolderError(R.string.subscription_field_invalid)
        }

        if (ibanEmpty) {
            setIbanError(R.string.iban_error_empty)
        }

        if (ibanInvalid) {
            setIbanError(R.string.iban_error_invalid)
        }

        if (ibanNoSepa) {
            setIbanError(R.string.iban_error_no_sepa)
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.trackSubscriptionPaymentFormScreen()
    }

    override fun done(): Boolean {
        var done = true
        if (viewBinding.fragmentSubscriptionBankIban.text.isNullOrBlank()) {
            done = false
            setIbanError(R.string.iban_error_empty)
            if (!viewBinding.fragmentSubscriptionBankAccountHolder.text.isNullOrBlank()) {
                viewModel.accountHolder =
                    viewBinding.fragmentSubscriptionBankAccountHolder.text.toString()
            }
        } else {
            try {
                val iban = IBAN.parse(viewBinding.fragmentSubscriptionBankIban.text)
                if (!iban.isSEPA) {
                    setIbanError(R.string.iban_error_no_sepa)
                    done = false
                }
            } catch (e: IllegalArgumentException) {
                setIbanError(R.string.iban_error_invalid)
                done = false
            }
        }
        viewModel.iban = viewBinding.fragmentSubscriptionBankIban.text.toString()
        viewModel.accountHolder = viewBinding.fragmentSubscriptionBankAccountHolder.text.toString()

        if (!done) {
            tracker.trackSubscriptionInquiryFormValidationErrorEvent()
        }
        return done
    }

    override fun next() {
        viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_ACCOUNT)
    }

    private fun setIbanError(@StringRes stringRes: Int) {
        viewBinding.fragmentSubscriptionBankIbanLayout.setError(stringRes)
    }

    private fun setAccountHolderError(@StringRes stringRes: Int) {
        viewBinding.fragmentSubscriptionBankIbanLayout.setError(stringRes)
    }
}