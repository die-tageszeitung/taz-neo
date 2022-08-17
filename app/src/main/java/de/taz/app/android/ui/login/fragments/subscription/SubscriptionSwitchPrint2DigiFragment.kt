package de.taz.app.android.ui.login.fragments.subscription

import android.content.Context
import android.os.Bundle
import android.view.View
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.SubscriptionFormDataType
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.databinding.FragmentSwitchFormBinding
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubscriptionSwitchPrint2DigiFragment : BaseMainFragment<FragmentSwitchFormBinding>() {
    private val log by Log

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
            val emailOrAboID = viewBinding.fragmentSwitchEmailAboID.text.toString().trim()
            val surname = viewBinding.fragmentSwitchSurname.text.toString().trim()
            val firstname = viewBinding.fragmentSwitchFirstName.text.toString().trim()
            val addressStreetNr = viewBinding.fragmentSwitchAddressStreet.text.toString().trim()
            val addressCity = viewBinding.fragmentSwitchAddressCity.text.toString().trim()
            val addressZipCode = viewBinding.fragmentSwitchAddressZipcode.text.toString().trim()
            val addressCountry = viewBinding.fragmentSwitchAddressCountry.text.toString().trim()
            val message = viewBinding.fragmentSwitchMessage.text.toString().trim()

            sendSwitchPrint2DigiForm(
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

    }


    private fun sendSwitchPrint2DigiForm(
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
                SubscriptionFormDataType.print2Digi,
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
            toastHelper.showToast("Switch form gesendet")
        }
        requireActivity().finish()
    }
}