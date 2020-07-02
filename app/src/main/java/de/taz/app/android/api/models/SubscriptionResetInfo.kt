package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubscriptionResetInfo(
    val status: SubscriptionResetStatus,
    val mail: String? = null
)

@JsonClass(generateAdapter = false)
enum class SubscriptionResetStatus {
    ok,
    invalidSubscriptionId,
    noMail,
    invalidConnection
}