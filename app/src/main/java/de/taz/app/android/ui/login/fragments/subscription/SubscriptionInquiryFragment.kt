package de.taz.app.android.ui.login.fragments.subscription

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.variables.SubscriptionFormDataType
import de.taz.app.android.databinding.FragmentSubscriptionInquiryFormBinding
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.login.fragments.LoginBaseFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.addAllLowercaseFilter
import de.taz.app.android.util.hideSoftInputKeyboard
import io.sentry.Sentry
import kotlinx.coroutines.launch

class SubscriptionExtendPrintPlusDigiFragment : SubscriptionInquiryFragment() {
    override val titleStringRes: Int = R.string.subscription_inquiry_extend_title
    override val descriptionStringRes: Int = R.string.subscription_inquiry_extend_description
    override val inquiryType: SubscriptionFormDataType =  SubscriptionFormDataType.printPlusDigi
}

class SubscriptionSwitchPrint2DigiFragment : SubscriptionInquiryFragment() {
    override val titleStringRes: Int = R.string.subscription_inquiry_switch_title
    override val descriptionStringRes: Int = R.string.subscription_inquiry_switch_description
    override val inquiryType: SubscriptionFormDataType =  SubscriptionFormDataType.print2Digi
}

abstract class SubscriptionInquiryFragment :
    LoginBaseFragment<FragmentSubscriptionInquiryFormBinding>() {

    protected abstract val titleStringRes: Int
    protected abstract val descriptionStringRes: Int
    protected abstract val inquiryType: SubscriptionFormDataType
    private val log by Log

    private lateinit var apiService: ApiService
    private lateinit var toastHelper: ToastHelper

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.apply {
            title.setText(titleStringRes)
            description.setText(descriptionStringRes)

            sendButton.setOnClickListener {
                validateAndSubmitForm()
            }

            cancelButton.setOnClickListener {
                back()
            }

            nestedScrollView.setOnTouchListener(object :
                View.OnTouchListener {
                @SuppressLint("ClickableViewAccessibility")
                override fun onTouch(view: View, event: MotionEvent): Boolean {
                    hideSoftInputKeyboard()
                    return false
                }
            })

            email.addAllLowercaseFilter()
        }
    }

    private fun validateAndSubmitForm() {
        showLoadingState()

        // Get the trimmed data from the ViewBinding
        val email = viewBinding.email.text?.trim()?.toString() ?: ""
        val subscriptionIdString = viewBinding.subscriptionId.text?.trim()?.toString() ?: ""
        val surname = viewBinding.surname.text?.trim()?.toString() ?: ""
        val firstname = viewBinding.firstName.text?.trim()?.toString() ?: ""
        val addressStreetNr = viewBinding.addressStreet.text?.trim()?.toString() ?: ""
        val addressCity = viewBinding.addressCity.text?.trim()?.toString() ?: ""
        val addressZipCode = viewBinding.addressZipcode.text?.trim()?.toString() ?: ""
        val addressCountry = viewBinding.addressCountry.text?.trim()?.toString() ?: ""
        val message = viewBinding.message.text?.trim()?.toString() ?: ""

        var isValid = true

        val subscriptionId = subscriptionIdString.toIntOrNull()
        if (!subscriptionIdString.isDigitsOnly()) {
            viewBinding.subscriptionId.error =
                getString(R.string.login_subscription_id_error_not_numeric)
            isValid = false
        }
        if (email.isEmpty()) {
            viewBinding.email.error = getString(R.string.login_email_error_empty)
            isValid = false
        }
        if (surname.isEmpty()) {
            viewBinding.surname.error = getString(R.string.login_surname_error_empty)
            isValid = false
        }
        if (firstname.isEmpty()) {
            viewBinding.firstName.error = getString(R.string.login_first_name_error_empty)
            isValid = false
        }
        if (addressStreetNr.isEmpty()) {
            viewBinding.addressStreet.error = getString(R.string.street_error_empty)
            isValid = false
        }
        if (addressZipCode.isEmpty()) {
            viewBinding.addressZipcode.error = getString(R.string.postcode_error_empty)
            isValid = false
        }
        if (addressCity.isEmpty()) {
            viewBinding.addressCity.error = getString(R.string.city_error_empty)
            isValid = false
        }
        if (addressCountry.isEmpty()) {
            viewBinding.addressCountry.error = getString(R.string.country_error_empty)
            isValid = false
        }

        if (isValid) {
            submitForm(
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
        } else {
            hideLoadingState()
        }
    }

    private fun submitForm(
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
        lifecycleScope.launch {
            try {
                val response = apiService.subscriptionFormData(
                    inquiryType,
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

                if (response.error == null) {
                    toastHelper.showToast(
                        R.string.subscription_inquiry_send_success_toast,
                        long = true
                    )
                    finishParentLoginActivity()

                } else {
                    // Ignore specific form field errors and simply show the message
                    val errorMessage = response.errorMessage ?: ""
                    showErrorToastAndResume(errorMessage)
                }

            } catch (e: ConnectivityException) {
                val errorMessage = resources.getString(R.string.toast_no_internet)
                showErrorToastAndResume(errorMessage)

            } catch (e: Exception) {
                val hint = "Could not submit subscriptionFormData"
                log.debug(hint, e)
                Sentry.captureException(e, hint)

                toastHelper.showToast(R.string.something_went_wrong_try_later)
                finishParentLoginActivity()
            }
        }
    }

    private fun showLoadingState() {
        hideSoftInputKeyboard()
        viewBinding.apply {
            loadingScreen.root.visibility = View.VISIBLE
            sendButton.isEnabled = false
        }
    }

    private fun hideLoadingState() {
        viewBinding.apply {
            loadingScreen.root.visibility = View.GONE
            sendButton.isEnabled = true
        }
    }

    private fun finishParentLoginActivity() {
        activity?.finish()
    }

    private fun showErrorToastAndResume(message: String) {
        val toastMessage = getString(R.string.subscription_inquiry_submission_error, message)
        toastHelper.showToast(toastMessage, long = true)
        hideLoadingState()
    }
}