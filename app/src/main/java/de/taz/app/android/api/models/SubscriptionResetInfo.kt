package de.taz.app.android.api.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionResetInfo(
    val status: SubscriptionResetStatus
)

@Serializable
enum class SubscriptionResetStatus {
    ok,
    invalidSubscriptionId,
    noMail,
    invalidConnection
}