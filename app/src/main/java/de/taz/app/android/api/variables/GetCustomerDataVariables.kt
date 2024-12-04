package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
data class GetCustomerDataVariables(
    val category: String,
    val name: String,
) : Variables