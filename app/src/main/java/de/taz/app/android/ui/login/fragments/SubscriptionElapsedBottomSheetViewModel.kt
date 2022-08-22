package de.taz.app.android.ui.login.fragments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.CustomerType
import de.taz.app.android.api.dto.SubscriptionFormDataType
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SubscriptionElapsedBottomSheetViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val apiService: ApiService = ApiService.getInstance(application)
    private val authHelper: AuthHelper = AuthHelper.getInstance(application)

    private val elapsedOnString = authHelper.elapsedDateMessage.asLiveData()
    val elapsedString = elapsedOnString.map { DateHelper.stringToLongLocalizedString(it) }

    private val _uiStateFlow = MutableStateFlow(UIState.INIT)
    val uiState = _uiStateFlow as StateFlow<UIState>

    fun sendMessage(message: String, contactMe: Boolean) {
        getApplicationScope().launch {
            val customerType = apiService.getCustomerType()
            val type = mapCustomer2SubscriptionFormDataType(customerType)

            if (type != null) {
                apiService.subscriptionFormData(
                    type = type,
                    message = message,
                    requestCurrentSubscriptionOpportunities = contactMe
                )
                _uiStateFlow.emit(UIState.SENT)
            } else {
                _uiStateFlow.emit(UIState.ERROR)
            }
        }
    }

    private fun mapCustomer2SubscriptionFormDataType(customerType: CustomerType?): SubscriptionFormDataType? {
        return when (customerType) {
            CustomerType.digital -> SubscriptionFormDataType.expiredDigiSubscription
            CustomerType.combo -> SubscriptionFormDataType.expiredDigiPrint
            CustomerType.sample -> SubscriptionFormDataType.trialSubscription
            else -> null
        }
    }

    enum class UIState {
        INIT,
        ERROR,
        SENT
    }

}