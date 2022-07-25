package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorDto(
    val message: String? = null,
    val extensions: ExtensionsDto? = null,
    val locations: List<ErrorLocationDto> = emptyList(),
    val path: List<String> = emptyList()
)

