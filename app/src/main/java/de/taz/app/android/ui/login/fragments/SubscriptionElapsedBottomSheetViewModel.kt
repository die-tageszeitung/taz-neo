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

    // region UIState
    enum class UIState {
        INIT,
        ERROR,
        SENT
    }
    // endregion


    // region singletons
    private val apiService: ApiService = ApiService.getInstance(application)
    private val authHelper: AuthHelper = AuthHelper.getInstance(application)
    // endregion

    // region logic
    fun sendMessage(message: String, contactMe: Boolean) {
        getApplicationScope().launch {
            customerTypeFlow.collect { customerType ->

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
    }

    private fun mapCustomer2SubscriptionFormDataType(customerType: CustomerType?): SubscriptionFormDataType? {
        return when (customerType) {
            CustomerType.digital -> SubscriptionFormDataType.expiredDigiSubscription
            CustomerType.combo -> SubscriptionFormDataType.expiredDigiPrint
            CustomerType.sample -> SubscriptionFormDataType.trialSubscription
            else -> null
        }
    }
    // endregion

    // region flows
    private val elapsedOnStringFlow = authHelper.elapsedDateMessage.asFlow()
    private val elapsedStringFlow = elapsedOnStringFlow.map { DateHelper.stringToLongLocalizedString(it) }

    val isElapsedFlow = authHelper.isElapsedFlow

    private val customerTypeFlow: Flow<CustomerType> = flow {
        val type = apiService.getCustomerType()
        if (type != null) {
            emit(type)
        }
    }

    private val _uiStateFlow = MutableStateFlow(UIState.INIT)
    val uiStateFlow = _uiStateFlow as StateFlow<UIState>

    private val typeStringFlow: Flow<String> = customerTypeFlow.map {
        when (it) {
            CustomerType.sample -> application.getString(R.string.trial_subscription)
            else -> application.getString(R.string.subscription)
        }
    }

    private val genitiveTypeStringFlow: Flow<String> = customerTypeFlow.map {
        when (it) {
            CustomerType.sample -> application.getString(R.string.trial_subscription_genitive)
            else -> application.getString(R.string.subscription_genitive)
        }
    }


    val elapsedTitleStringFlow: Flow<String> = typeStringFlow.map { typeString ->
        elapsedStringFlow.first()?.let {
            application.getString(
                R.string.popup_login_elapsed_header,
                typeString,
                it,
            )
        } ?: application.getString(R.string.popup_login_elapsed_header_no_date, typeString)
    }

    val elapsedDescriptionStringFlow: Flow<String> = genitiveTypeStringFlow.map {
        application.getString(
            R.string.popup_login_elapsed_text,
            it,
        )
    }
    // endregion
}
