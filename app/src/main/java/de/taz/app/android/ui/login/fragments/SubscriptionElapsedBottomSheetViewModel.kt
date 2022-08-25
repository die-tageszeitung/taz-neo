package de.taz.app.android.ui.login.fragments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.CustomerType
import de.taz.app.android.api.dto.SubscriptionFormDataType
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SubscriptionElapsedBottomSheetViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val apiService: ApiService = ApiService.getInstance(application)
    private val authHelper: AuthHelper = AuthHelper.getInstance(application)

    private val elapsedOnString = authHelper.elapsedDateMessage.asFlow()
    private val elapsedString = elapsedOnString.map { DateHelper.stringToLongLocalizedString(it) }

    val isElapsed = authHelper.isElapsedFlow

    val customerType: Flow<CustomerType> = flow {
        val type = authHelper.customerType.get() ?: apiService.getCustomerType()
        if (type != null) {
            emit(type)
        }
    }

    private val _uiStateFlow = MutableStateFlow(UIState.INIT)
    val uiState = _uiStateFlow as StateFlow<UIState>

    fun sendMessage(message: String, contactMe: Boolean) {
        getApplicationScope().launch {
            customerType.collect { customerType ->
                // fallback to use if customerType is demo but we have stored a value in authHelper
                // TODO remove once tokens for elapsed trialSubscription login implemented
                val failsafeType = if (customerType == CustomerType.demo) {
                    authHelper.customerType.get() ?: customerType
                } else {
                    null
                }

                val type = mapCustomer2SubscriptionFormDataType(failsafeType)

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

    private val typeString: Flow<String> = customerType.map {
        when (it) {
            CustomerType.sample -> application.getString(R.string.trial_subscription)
            else -> application.getString(R.string.subscription)
        }
    }

    private val genitiveTypeString: Flow<String> = customerType.map {
        when (it) {
            CustomerType.sample -> application.getString(R.string.trial_subscription_genitive)
            else -> application.getString(R.string.subscription_genitive)
        }
    }


    val elapsedTitleString: Flow<String> = typeString.map { typeString ->
        elapsedString.first()?.let {
            application.getString(
                R.string.popup_login_elapsed_header,
                typeString,
                it,
            )
        } ?: application.getString(R.string.popup_login_elapsed_header_no_date, typeString)
    }

    val elapsedDescriptionString: Flow<String> = genitiveTypeString.map {
        application.getString(
            R.string.popup_login_elapsed_text,
            it,
        )
    }
}
