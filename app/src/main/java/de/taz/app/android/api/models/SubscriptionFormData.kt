package de.taz.app.android.api.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionFormData(
    val error: SubscriptionFormDataError? = null,
    val errorMessage: String? = null,
)
