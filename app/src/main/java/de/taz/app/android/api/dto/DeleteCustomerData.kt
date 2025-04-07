package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeleteCustomerData(
    val error: String? = null,
    val ok: Boolean? = null,
)