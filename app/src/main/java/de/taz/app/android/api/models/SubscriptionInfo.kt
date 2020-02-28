package de.taz.app.android.api.models

data class SubscriptionInfo(
    val status: SubscriptionStatus,
    val message: String? = null,
    val token: String? = null
)
