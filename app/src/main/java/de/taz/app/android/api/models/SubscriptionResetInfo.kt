package de.taz.app.android.api.models

data class SubscriptionResetInfo(
    val status: SubscriptionResetStatus,
    val mail: String? = null
)

enum class SubscriptionResetStatus {
    ok,
    invalidSubscriptionId,
    noMail,
    invalidConnection
}