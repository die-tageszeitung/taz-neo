package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubscriptionInfo(
    val status: SubscriptionStatus,
    val message: String? = null,
    val token: String? = null
)
