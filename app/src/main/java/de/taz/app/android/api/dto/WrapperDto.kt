package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class WrapperDto (
    val data: DataDto? = null,
    val errors: List<ErrorDto> = emptyList()
)