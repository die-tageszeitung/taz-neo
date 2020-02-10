package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.models.AuthInfo
import de.taz.app.android.api.models.SubscriptionInfo

@JsonClass(generateAdapter = true)
data class DataDto (
    val product: ProductDto? = null,
    val subscriptionPoll: SubscriptionInfo? = null,
    val authentificationToken: AuthTokenInfoDto? = null,
    val downloadStop: Boolean? = null,
    val downloadStart: String? = null,
    val notification: Boolean? = null,
    val checkSubscriptionId: AuthInfo? = null
)