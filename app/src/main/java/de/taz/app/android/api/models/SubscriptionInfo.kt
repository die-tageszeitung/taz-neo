package de.taz.app.android.api.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionInfo(
    val status: SubscriptionStatus,
    val message: String? = null,
    val token: String? = null
)
