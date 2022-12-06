package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionInfoDto(
    val status: SubscriptionStatusDto,
    val message: String? = null,
    val token: String? = null
)
