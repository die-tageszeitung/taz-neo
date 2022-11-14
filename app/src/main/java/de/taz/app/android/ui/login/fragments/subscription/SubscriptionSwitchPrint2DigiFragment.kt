package de.taz.app.android.ui.login.fragments.subscription

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.text.isDigitsOnly
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.SubscriptionFormDataType
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.databinding.FragmentSwitchFormBinding
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubscriptionSwitchPrint2DigiFragment : BaseMainFragment<FragmentSwitchFormBinding>() {

    private lateinit var apiService: ApiService
    private lateinit var toastHelper: ToastHelper

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(requireContext().applicationContext)
        toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentSwitchSendButton.setOnClickListener {
            viewBinding.loadingScreen.root.visibility = View.VISIBLE
            val email = viewBinding.fragmentSwitchEmail.text.toString().trim()
            val subscriptionIdString = viewBinding.fragmentSwitchSubscriptionId.text.toString().trim()
            val surname = viewBinding.fragmentSwitchSurname.text.toString().trim()
            val firstname = viewBinding.fragmentSwitchFirstName.text.toString().trim()
            val addressStreetNr = viewBinding.fragmentSwitchAddressStreet.text.toString().trim()
            val addressCity = viewBinding.fragmentSwitchAddressCity.text.toString().trim()
            val addressZipCode = viewBinding.fragmentSwitchAddressZipcode.text.toString().trim()
            val addressCountry = viewBinding.fragmentSwitchAddressCountry.text.toString().trim()
            val message = viewBinding.fragmentSwitchMessage.text.toString().trim()

            val necessaryCredentialsPresent =
                email.isNotEmpty() && surname.isNotEmpty() && firstname.isNotEmpty()
                        && addressStreetNr.isNotEmpty() && addressZipCode.isNotEmpty()
                        && addressCity.isNotEmpty() && addressCountry.isNotEmpty()

            val isSubscriptionIdNumeric = subscriptionIdString.isDigitsOnly()

            if (necessaryCredentialsPresent && isSubscriptionIdNumeric) {
                val subscriptionId = subscriptionIdString.toInt()
                sendSwitchPrint2DigiForm(
                    email,
                    subscriptionId,
                    surname,
                    firstname,
                    addressStreetNr,
                    addressCity,
                    addressZipCode,
                    addressCountry,
                    message
                )
            }
            else {
                if (!isSubscriptionIdNumeric) {
                    viewBinding.fragmentSwitchSubscriptionId.error = requireContext().getString(R.string.login_subscription_id_error_not_numeric)
                }
                if (email.isEmpty()) {
                    viewBinding.fragmentSwitchEmail.error = requireContext().getString(R.string.login_email_error_empty)
                }
                if (surname.isEmpty()) {
                    viewBinding.fragmentSwitchSurname.error = requireContext().getString(R.string.login_surname_error_empty)
                }
                if (firstname.isEmpty()) {
                    viewBinding.fragmentSwitchFirstName.error = requireContext().getString(R.string.login_first_name_error_empty)
                }
                if (addressStreetNr.isEmpty()) {
                    viewBinding.fragmentSwitchAddressStreet.error = requireContext().getString(R.string.street_error_empty)
                }
                if (addressZipCode.isEmpty()) {
                    viewBinding.fragmentSwitchAddressZipcode.error = requireContext().getString(R.string.postcode_error_empty)
                }
                if (addressCity.isEmpty()) {
                    viewBinding.fragmentSwitchAddressCity.error = requireContext().getString(R.string.city_error_empty)
                }
                if (addressCountry.isEmpty()) {
                    viewBinding.fragmentSwitchAddressCountry.error = requireContext().getString(R.string.country_error_empty)
                }
                viewBinding.loadingScreen.root.visibility = View.GONE
            }
        }

        viewBinding.fragmentSwitchNestedScrollView.setOnTouchListener(object :
            View.OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                hideSoftInputKeyboard()
                return false
            }
        })
    }


    private fun sendSwitchPrint2DigiForm(
        email: String?,
        subscriptionId: Int?,
        surname: String?,
        firstName: String?,
        addressStreetNr: String?,
        addressCity: String?,
        addressZipCode: String?,
        addressCountry: String?,
        message: String?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            apiService.subscriptionFormData(
                SubscriptionFormDataType.print2Digi,
                email,
                subscriptionId,
                surname,
                firstName,
                addressStreetNr,
                addressCity,
                addressZipCode,
                addressCountry,
                message,
                false
            )
            toastHelper.showToast(R.string.subscription_inquiry_send_success_toast, long=true)
        }
        requireActivity().finish()
    }
}