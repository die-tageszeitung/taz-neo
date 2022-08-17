package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.map
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.taz.app.android.R
import de.taz.app.android.TazApplication
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.CustomerType
import de.taz.app.android.api.dto.SubscriptionFormDataType
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentSubscriptionElapsedDialogBinding
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import kotlinx.coroutines.launch

private class SubscriptionElapsedDialogFragmentViewModel(application: TazApplication): AndroidViewModel(application) {
    private val apiService = ApiService.getInstance(application)
    private val authHelper = AuthHelper.getInstance(application)

    private val elapsedOnString = authHelper.elapsedDateMessage.asLiveData()
    val elapsedString = elapsedOnString.map { DateHelper.stringToLongLocalizedString(it) }

    fun sendMessage(message: String, contactMe: Boolean) {
        getApplicationScope().launch {
            val customerType = apiService.getCustomerType()
            apiService.subscriptionFormData(
                type = mapCustomer2SubscriptionFormDataType(customerType),
                message = message,
                requestCurrentSubscriptionOpportunities = contactMe
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

class SubscriptionElapsedDialogFragment : ViewBindingBottomSheetFragment<FragmentSubscriptionElapsedDialogBinding>() {

    private val viewModel by viewModels<SubscriptionElapsedDialogFragmentViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.elapsedString.observe(this) {
            viewBinding.title.text =
                getString(R.string.popup_login_elapsed_header, it)
        }

        viewBinding.sendButton.setOnClickListener {
            viewModel.sendMessage(
                viewBinding.messageToSubscriptionService.text.toString(),
                viewBinding.letTheSubscriptionServiceContactYouCheckbox.isChecked
            )
        }
    }

    override fun onResume() {
        super.onResume()
        (dialog as BottomSheetDialog).behavior.apply {
            isFitToContents = false
        }
    }
}