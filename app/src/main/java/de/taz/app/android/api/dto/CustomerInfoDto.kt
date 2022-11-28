package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomerInfoDto(
    val authInfo: AuthInfoDto,
    val customerType: CustomerTypeDto? = null,
    val subscriptionType: SubscriptionTypeDto? = null,
    val cancellation: String? = null,
    val sampleType: String? = null
)
