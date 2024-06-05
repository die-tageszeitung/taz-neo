package de.taz.app.android.ui.login.fragments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.CustomerType
import de.taz.app.android.api.variables.SubscriptionFormDataType
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.util.Log
import de.taz.app.android.sentry.SentryWrapper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val MESSAGE_MIN_LENGTH = 12

class SubscriptionElapsedBottomSheetViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val log by Log

    // region UIState
    sealed class UIState {
        object Init : UIState()
        object FormInvalidMessageLength : UIState()
        class SubmissionError(val message: String) : UIState()
        object UnexpectedFailure: UIState()
        object Sent : UIState()
    }
    // endregion


    // region singletons
    private val apiService: ApiService = ApiService.getInstance(application)
    private val authHelper: AuthHelper = AuthHelper.getInstance(application)
    // endregion

    // region logic
    fun sendMessage(message: String, contactMe: Boolean) {
        if (message.length < MESSAGE_MIN_LENGTH) {
            _uiStateFlow.value = UIState.FormInvalidMessageLength
            return
        }

        getApplicationScope().launch {
            customerTypeFlow.collect { customerType ->
                val type = mapCustomer2SubscriptionFormDataType(customerType)
                when {
                    type != null -> submitForm(type, message, contactMe)
                    // If there is no customerType at all we don't have no internet
                    customerType == null -> showNoInternetError()
                    else -> {
                        log.warn("Could not map customer type to subscription form data type: $customerType")
                        _uiStateFlow.emit(UIState.UnexpectedFailure)
                    }
                }
            }
        }
    }

    private suspend fun submitForm(type: SubscriptionFormDataType, message: String, requestCurrentSubscriptionOpportunities: Boolean ) {
        try {
            val response = apiService.subscriptionFormData(
                type = type,
                message = message,
                requestCurrentSubscriptionOpportunities = requestCurrentSubscriptionOpportunities
            )

            if (response.error == null) {
                _uiStateFlow.emit(UIState.Sent)
                authHelper.elapsedFormAlreadySent.set(true)
            } else {
                // Ignore specific form field errors and simply show the message
                val message = response.errorMessage ?: ""
                _uiStateFlow.emit(UIState.SubmissionError(message))
            }

        } catch (e: ConnectivityException) {
            showNoInternetError()

        } catch (e: Exception) {
            log.warn("Could not submit subscriptionFormData", e)
            SentryWrapper.captureException(e)
            _uiStateFlow.emit(UIState.UnexpectedFailure)
        }
    }

    private suspend fun showNoInternetError() {
        val resources = getApplication<Application>().resources
        val message = resources.getString(R.string.toast_no_internet)
        _uiStateFlow.emit(UIState.SubmissionError(message))
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
    private val elapsedStringFlow =
        elapsedOnStringFlow.map { DateHelper.stringToLongLocalizedString(it) }

    val isElapsedFlow = authHelper.isElapsedFlow

    // This is a cold flow that will be executed each time it is collected.
    // For example when mapped by elapsedTitleStringFlow and elapsedDescriptionStringFlow.
    private val customerTypeFlow: Flow<CustomerType?> = flow {
        val type = try {
            apiService.getCustomerType()
        } catch (e: ConnectivityException) {
            null
        }
        emit(type)
    }

    private val _uiStateFlow = MutableStateFlow<UIState>(UIState.Init)
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

    /**
     * To be called when a one time error on [uiStateFlow] was consumed and shown to the user.
     */
    fun errorWasHandled() {
        _uiStateFlow.value = UIState.Init
    }
    // endregion
}