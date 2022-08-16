package de.taz.app.android.api.variables

import de.taz.app.android.api.dto.SubscriptionFormDataType
import kotlinx.serialization.Serializable


@Serializable
data class SubscriptionFormDataVariables(
    val subscriptionFormDataType: SubscriptionFormDataType,
    val mail: String?,
    val surname: String?,
    val firstname: String?,
    val street: String?,
    val city: String?,
    val postcode: String?,
    val country: String?,
    val message: String?,
    val requestCurrentSubscriptionOpportunities: Boolean?
) : Variables