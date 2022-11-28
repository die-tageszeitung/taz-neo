package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionFormDataDto(
    val error: SubscriptionFormDataErrorDto? = null,
    val errorMessage: String? = null,
)

