package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorLocationDto(
    val line: Int? = null,
    val column: Int? = null
)
