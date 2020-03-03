package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.models.AuthInfo
import de.taz.app.android.api.models.PasswordResetInfo
import de.taz.app.android.api.models.SubscriptionInfo
import de.taz.app.android.api.models.SubscriptionResetInfo

@JsonClass(generateAdapter = true)
data class DataDto (
    val product: ProductDto? = null,
    val trialSubscription: SubscriptionInfo? = null,
    val subscriptionId2tazId: SubscriptionInfo? = null,
    val subscriptionPoll: SubscriptionInfo? = null,
    val authentificationToken: AuthTokenInfoDto? = null,
    val downloadStop: Boolean? = null,
    val downloadStart: String? = null,
    val notification: Boolean? = null,
    val checkSubscriptionId: AuthInfo? = null,
    val errorReport: Boolean? = null,
    val passwordReset: PasswordResetInfo? = null,
    val subscriptionReset: SubscriptionResetInfo? = null
)