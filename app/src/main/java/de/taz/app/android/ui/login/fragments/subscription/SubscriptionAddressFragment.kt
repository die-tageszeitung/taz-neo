package de.taz.app.android.ui.login.fragments.subscription

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentSubscriptionAddressBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.setError
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.login.LoginViewModelState

const val MAX_NAME_LENGTH = 24

class SubscriptionAddressFragment :
    SubscriptionBaseFragment<FragmentSubscriptionAddressBinding>() {

    private lateinit var tracker: Tracker

    var cityInvalid: Boolean = false
    var countryInvalid: Boolean = false
    var postcodeInvalid: Boolean = false
    var streetInvalid: Boolean = false
    var nameTooLong: Boolean = false
    var firstNameEmpty: Boolean = false
    var firstNameInvalid: Boolean = false
    var surnameEmpty: Boolean = false
    var surnameInvalid: Boolean = false

    companion object {
        fun newInstance(
            cityInvalid: Boolean = false,
            countryInvalid: Boolean = false,
            postcodeInvalid: Boolean = false,
            streetInvalid: Boolean = false,
            nameTooLong: Boolean = false,
            firstNameEmpty: Boolean = false,
            firstNameInvalid: Boolean = false,
            surnameEmpty: Boolean = false,
            surnameInvalid: Boolean = false
        ): SubscriptionAddressFragment {
            val fragment = SubscriptionAddressFragment()
            fragment.cityInvalid = cityInvalid
            fragment.countryInvalid = countryInvalid
            fragment.postcodeInvalid = postcodeInvalid
            fragment.streetInvalid = streetInvalid
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
            if (viewModel.price == 0) {
                fragmentSubscriptionAddressStreetLayout.visibility = View.GONE
                fragmentSubscriptionAddressCityLayout.visibility = View.GONE
                fragmentSubscriptionAddressPostcodeLayout.visibility = View.GONE
                fragmentSubscriptionAddressCountryLayout.visibility = View.GONE
                fragmentSubscriptionAddressPhoneLayout.visibility = View.GONE
                fragmentSubscriptionAddressNameAffix.imeOptions = EditorInfo.IME_ACTION_DONE
            }

            fragmentSubscriptionAddressFirstName.setText(viewModel.firstName)
            fragmentSubscriptionAddressSurname.setText(viewModel.surName)
            fragmentSubscriptionAddressStreet.setText(viewModel.street)
            fragmentSubscriptionAddressCity.setText(viewModel.city)
            fragmentSubscriptionAddressCountry.setText(viewModel.country)
            fragmentSubscriptionAddressPostcode.setText(viewModel.postCode)

            fragmentSubscriptionAddressFirstName.doAfterTextChanged { text ->
                fragmentSubscriptionAddressSurnameLayout.counterMaxLength =
                    (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
            }

            fragmentSubscriptionAddressSurname.doAfterTextChanged { text ->
                fragmentSubscriptionAddressFirstNameLayout.counterMaxLength =
                    (MAX_NAME_LENGTH - (text?.length ?: 0)).coerceIn(1, MAX_NAME_LENGTH - 1)
            }

            fragmentSubscriptionAddressPhone.setOnEditorActionListener(
                OnEditorActionDoneListener(::ifDoneNext)
            )

            fragmentSubscriptionAddressNameAffix.setOnEditorActionListener(
                OnEditorActionDoneListener(::ifDoneNext)
            )

            fragmentSubscriptionAddressProceed.setOnClickListener {
                ifDoneNext()
            }

            backButton.setOnClickListener {
                back()
            }

            cancelButton.setOnClickListener {
                finish()
            }

            if (nameTooLong) {
                setFirstNameError(R.string.login_first_name_helper)
                setSurnameError(R.string.login_surname_helper)
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

            if (cityInvalid) {
                fragmentSubscriptionAddressCityLayout.setError(R.string.subscription_field_invalid)
            }
            if (countryInvalid) {
                fragmentSubscriptionAddressCountryLayout.setError(R.string.subscription_field_invalid)
            }
            if (streetInvalid) {
                fragmentSubscriptionAddressStreetLayout.setError(R.string.subscription_field_invalid)
            }
            if (postcodeInvalid) {
                fragmentSubscriptionAddressPostcodeLayout.setError(R.string.subscription_field_invalid)
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
            if (fragmentSubscriptionAddressFirstName.text.isNullOrBlank()) {
                setFirstNameError(R.string.login_first_name_error_empty)
                done = false
            }
            if (fragmentSubscriptionAddressSurname.text.isNullOrBlank()) {
                setSurnameError(R.string.login_surname_error_empty)
                done = false
            }
            if (fragmentSubscriptionAddressStreetLayout.isVisible
                && fragmentSubscriptionAddressStreet.text.isNullOrBlank()
            ) {
                fragmentSubscriptionAddressStreetLayout.error =
                    context?.getString(R.string.street_error_empty)
                done = false
            }
            if (fragmentSubscriptionAddressCityLayout.isVisible
                && fragmentSubscriptionAddressCity.text.isNullOrBlank()
            ) {
                fragmentSubscriptionAddressCityLayout.error =
                    context?.getString(R.string.city_error_empty)
                done = false
            }
            if (fragmentSubscriptionAddressCountryLayout.isVisible
                && fragmentSubscriptionAddressCountry.text.isNullOrBlank()
            ) {
                fragmentSubscriptionAddressCountryLayout.error =
                    context?.getString(R.string.country_error_empty)
                done = false
            }
            if (fragmentSubscriptionAddressPostcodeLayout.isVisible
                && fragmentSubscriptionAddressPostcode.text.isNullOrBlank()
            ) {
                fragmentSubscriptionAddressPostcodeLayout.error =
                    context?.getString(R.string.postcode_error_empty)
                done = false
            }
            viewModel.apply {
                firstName = fragmentSubscriptionAddressFirstName.text.toString()
                surName = fragmentSubscriptionAddressSurname.text.toString()
                street = fragmentSubscriptionAddressStreet.text.toString()
                city = fragmentSubscriptionAddressCity.text.toString()
                country = fragmentSubscriptionAddressCountry.text.toString()
                postCode = fragmentSubscriptionAddressPostcode.text.toString()
                phone = fragmentSubscriptionAddressPhone.text.toString()
            }
        }
        if (!done) {
            tracker.trackSubscriptionInquiryFormValidationErrorEvent()
        }
        return done
    }

    override fun next() {
        if (viewModel.price == 0) {
            viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_ACCOUNT)
        } else {
            viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_BANK)

        }
    }

    private fun setFirstNameError(@StringRes stringRes: Int) {
        viewBinding.fragmentSubscriptionAddressFirstNameLayout.setError(stringRes)
    }

    private fun setSurnameError(@StringRes stringRes: Int) {
        viewBinding.fragmentSubscriptionAddressSurnameLayout.setError(stringRes)
    }
}