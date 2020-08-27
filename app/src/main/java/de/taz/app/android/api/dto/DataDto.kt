package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.models.*

@JsonClass(generateAdapter = true)
data class DataDto (
    val authentificationToken: AuthTokenInfo? = null,
    val checkSubscriptionId: AuthInfo? = null,
    val downloadStop: Boolean? = null,
    val downloadStart: String? = null,
    val errorReport: Boolean? = null,
    val notification: Boolean? = null,
    val passwordReset: PasswordResetInfo? = null,
    val priceList: List<PriceInfo>? = null,
    val product: ProductDto? = null,
    val subscription: SubscriptionInfo? = null,
    val subscriptionId2tazId: SubscriptionInfo? = null,
    val subscriptionPoll: SubscriptionInfo? = null,
    val subscriptionReset: SubscriptionResetInfo? = null,
    val trialSubscription: SubscriptionInfo? = null
)