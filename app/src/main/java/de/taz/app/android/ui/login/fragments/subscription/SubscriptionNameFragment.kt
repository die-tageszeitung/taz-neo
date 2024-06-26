package de.taz.app.android.ui.login.fragments.subscription

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentSubscriptionNameBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.setError
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.login.LoginViewModelState


const val MAX_NAME_LENGTH = 24

class SubscriptionNameFragment :
    SubscriptionBaseFragment<FragmentSubscriptionNameBinding>() {

    private lateinit var tracker: Tracker

    var nameTooLong: Boolean = false
    var firstNameEmpty: Boolean = false
    var firstNameInvalid: Boolean = false
    var surnameEmpty: Boolean = false
    var surnameInvalid: Boolean = false

    companion object {
        fun newInstance(
            nameTooLong: Boolean = false,
            firstNameEmpty: Boolean = false,
            firstNameInvalid: Boolean = false,
            surnameEmpty: Boolean = false,
            surnameInvalid: Boolean = false
        ): SubscriptionNameFragment {
            val fragment = SubscriptionNameFragment()
            fragment.nameTooLong = nameTooLong
            fragment.firstNameEmpty = firstNameEmpty
            fragment.firstNameInvalid = firstNameInvalid
            fragment.surnameEmpty = surnameEmpty
            fragment.surnameInvalid = surnameInvalid
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.apply {
            fragmentSubscriptionNameNameAffix.imeOptions = EditorInfo.IME_ACTION_DONE
            fragmentSubscriptionNameFirstName.setText(viewModel.firstName)
            fragmentSubscriptionNameSurname.setText(viewModel.surName)

            fragmentSubscriptionNameNameAffix.setOnEditorActionListener(
                OnEditorActionDoneListener(::ifDoneNext)
            )

            fragmentSubscriptionNameProceed.setOnClickListener {
                ifDoneNext()
            }

            backButton.setOnClickListener {
                loginFlowBack()
            }

            if (nameTooLong) {
                setNameTooLongError()
            }

            if (firstNameEmpty) {
                setFirstNameError(R.string.login_first_name_error_empty)
            }
            if (firstNameInvalid) {
                setFirstNameError(R.string.login_first_name_error_invalid)
            }
            if (surnameEmpty) {
                setSurnameError(R.string.login_surname_error_empty)
            }
            if (surnameInvalid) {
                setSurnameError(R.string.login_surname_error_invalid)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.trackSubscriptionPersonalDataFormScreen()
    }

    override fun done(): Boolean {
        var done = true
        viewBinding.apply {
            if (fragmentSubscriptionNameFirstName.text.isNullOrBlank()) {
                setFirstNameError(R.string.login_first_name_error_empty)
                done = false
            }
            if (fragmentSubscriptionNameSurname.text.isNullOrBlank()) {
                setSurnameError(R.string.login_surname_error_empty)
                done = false
            }
            val combinedName = fragmentSubscriptionNameFirstName.text.toString() + fragmentSubscriptionNameSurname.text.toString()
            if (combinedName.length > MAX_NAME_LENGTH) {
                setNameTooLongError()
                done = false
            }
            viewModel.apply {
                firstName = fragmentSubscriptionNameFirstName.text.toString()
                surName = fragmentSubscriptionNameSurname.text.toString()
            }
        }
        if (!done) {
            tracker.trackSubscriptionInquiryFormValidationErrorEvent()
        }
        return done
    }

    override fun next() {
        viewModel.status = LoginViewModelState.SUBSCRIPTION_ACCOUNT
    }

    private fun setFirstNameError(@StringRes stringRes: Int) {
        viewBinding.fragmentSubscriptionNameFirstNameLayout.setError(stringRes)
    }

    private fun setSurnameError(@StringRes stringRes: Int) {
        viewBinding.fragmentSubscriptionNameSurnameLayout.setError(stringRes)
    }

    private fun setNameTooLongError() {
        setFirstNameError(R.string.login_first_name_helper)
        setSurnameError(R.string.login_surname_helper)
    }
}