package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.CustomerType
import de.taz.app.android.api.dto.SubscriptionFormDataType
import de.taz.app.android.monkey.getApplicationScope
import kotlinx.coroutines.launch

class SubscriptionElapsedDialogFragment : BottomSheetDialogFragment() {
    private lateinit var apiService: ApiService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        inflater.inflate(R.layout.fragment_subscription_elapsed_dialog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        apiService = ApiService.getInstance(requireContext().applicationContext)

        view.findViewById<Button>(R.id.send_button).setOnClickListener {
            sendMessage()
        }
    }

    private fun sendMessage() {
        getApplicationScope().launch {
            val message =
                requireView().findViewById<EditText>(R.id.message_to_subscription_service)?.text.toString()
            val isChecked =
                requireView().findViewById<CheckBox>(R.id.let_the_subscription_service_contact_you_checkbox)?.isChecked
            val customerType = apiService.getCustomerType()
            apiService.subscriptionFormData(
                type = mapCustomer2SubscriptionFormDataType(customerType),
                message = message,
                requestCurrentSubscriptionOpportunities = isChecked
            )
        }
    }

    private fun mapCustomer2SubscriptionFormDataType(customerType: CustomerType?): SubscriptionFormDataType {
        return when (customerType) {
            CustomerType.digital -> SubscriptionFormDataType.expiredDigiSubscription
            CustomerType.combo -> SubscriptionFormDataType.expiredDigiPrint
            CustomerType.sample -> SubscriptionFormDataType.trialSubscription
            else -> SubscriptionFormDataType.unknown
        }
    }
}