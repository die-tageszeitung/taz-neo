package de.taz.app.android.api.models

import de.taz.app.android.api.dto.CustomerType
import de.taz.app.android.api.dto.SubscriptionType
import kotlinx.serialization.Serializable

@Serializable
data class CustomerInfo(
    val authInfo: AuthInfo,
    val customerType: CustomerType? = null,
    val subscriptionType: SubscriptionType? = null,
    val cancellation: String? = null,
    val sampleType: String? = null
)
