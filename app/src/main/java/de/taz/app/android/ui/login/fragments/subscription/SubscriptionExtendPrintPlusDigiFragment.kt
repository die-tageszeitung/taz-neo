package de.taz.app.android.ui.login.fragments.subscription

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.SubscriptionFormDataType
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.databinding.FragmentExtendFormBinding
import de.taz.app.android.singletons.ToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubscriptionExtendPrintPlusDigiFragment: BaseMainFragment<FragmentExtendFormBinding>() {
    private lateinit var apiService: ApiService
    private lateinit var toastHelper: ToastHelper

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(requireContext().applicationContext)
        toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentExtendSendButton.setOnClickListener {
            viewBinding.loadingScreen.root.visibility = View.VISIBLE
            val emailOrAboID = viewBinding.fragmentExtendEmailAboID.text.toString().trim()
            val surname = viewBinding.fragmentExtendSurname.text.toString().trim()
            val firstname = viewBinding.fragmentExtendFirstName.text.toString().trim()
            val addressStreetNr = viewBinding.fragmentExtendAddressStreet.text.toString().trim()
            val addressCity = viewBinding.fragmentExtendAddressCity.text.toString().trim()
            val addressZipCode = viewBinding.fragmentExtendAddressZipcode.text.toString().trim()
            val addressCountry = viewBinding.fragmentExtendAddressCountry.text.toString().trim()
            val message = viewBinding.fragmentExtendMessage.text.toString().trim()

            val necessaryCredentialsPresent =
                surname.isNotEmpty() && firstname.isNotEmpty() && addressStreetNr.isNotEmpty()
                        && addressZipCode.isNotEmpty() && addressCity.isNotEmpty() && addressCountry.isNotEmpty()

            if (necessaryCredentialsPresent) {
                sendExtendPrintPlusDigiForm(
                    emailOrAboID,
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
                if (surname.isEmpty()) {
                    viewBinding.fragmentExtendSurname.error = requireContext().getString(R.string.login_surname_error_empty)
                }
                if (firstname.isEmpty()) {
                    viewBinding.fragmentExtendFirstName.error = requireContext().getString(R.string.login_first_name_error_empty)
                }
                if (addressStreetNr.isEmpty()) {
                    viewBinding.fragmentExtendAddressStreet.error = requireContext().getString(R.string.street_error_empty)
                }
                if (addressZipCode.isEmpty()) {
                    viewBinding.fragmentExtendAddressZipcode.error = requireContext().getString(R.string.postcode_error_empty)
                }
                if (addressCity.isEmpty()) {
                    viewBinding.fragmentExtendAddressCity.error = requireContext().getString(R.string.city_error_empty)
                }
                if (addressCountry.isEmpty()) {
                    viewBinding.fragmentExtendAddressCountry.error = requireContext().getString(R.string.country_error_empty)
                }
                viewBinding.loadingScreen.root.visibility = View.GONE
            }
        }

        viewBinding.fragmentExtendNestedScrollView.setOnTouchListener(object :
            View.OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                hideKeyBoard()
                return false
            }
        })
    }


    private fun sendExtendPrintPlusDigiForm(
        emailOrAboID: String?,
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
                SubscriptionFormDataType.printPlusDigi,
                emailOrAboID,
                surname,
                firstName,
                addressStreetNr,
                addressCity,
                addressZipCode,
                addressCountry,
                message,
                false
            )
            toastHelper.showToast(R.string.subscription_inquiry_send_success_toast, long = true)
        }
        requireActivity().finish()
    }
}