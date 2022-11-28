package de.taz.app.android.api.models

data class SubscriptionResetInfo(
    val status: SubscriptionResetStatus
)

enum class SubscriptionResetStatus {
    ok,
    invalidSubscriptionId,
    noMail,
    invalidConnection,
    UNKNOWN_RESPONSE
}