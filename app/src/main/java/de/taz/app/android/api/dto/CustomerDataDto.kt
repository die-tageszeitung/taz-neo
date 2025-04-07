package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomerDataDto(
    val time: String,
    val category: String,
    val name: String,
    // TODO(eike): Ask Ralf to replace the graphql identifier to something else then val:
    val `val`: String? = null,
)
