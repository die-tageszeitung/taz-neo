package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
data class SaveCustomerDataVariables(
    val category: String,
    val name: String,
    val `val`: String,
) : Variables