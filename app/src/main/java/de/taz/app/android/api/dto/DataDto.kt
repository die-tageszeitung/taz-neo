package de.taz.app.android.api.dto

import de.taz.app.android.api.models.PasswordResetInfo
import kotlinx.serialization.Serializable

@Serializable
data class DataDto (
    val authentificationToken: AuthTokenInfoDto? = null,
    val app: AppDto? = null,
    val cancellation: CancellationStatusDto? = null,
    val checkSubscriptionId: AuthInfoDto? = null,
    val customerInfo: CustomerInfoDto? = null,
    val deleteCustomerData: DeleteCustomerData? = null,
    val downloadStop: Boolean? = null,
    val downloadStart: String? = null,
    val errorReport: Boolean? = null,
    val getCustomerData: GetCustomerData? = null,
    val notification: Boolean? = null,
    val passwordReset: PasswordResetInfo? = null,
    val product: ProductDto? = null,
    val saveCustomerData: SaveCustomerData? = null,
    val search: SearchDto? = null,
    val subscription: SubscriptionInfoDto? = null,
    val subscriptionId2tazId: SubscriptionInfoDto? = null,
    val subscriptionPoll: SubscriptionInfoDto? = null,
    val subscriptionReset: SubscriptionResetInfoDto? = null,
    val trialSubscription: SubscriptionInfoDto? = null,
    val subscriptionFormData: SubscriptionFormDataDto? = null,
)