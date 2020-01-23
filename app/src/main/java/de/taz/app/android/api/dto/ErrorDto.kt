package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ErrorDto(
    val message: String? = null,
    val category: String? = null,
    val locations: List<ErrorLocationDto> = emptyList()
)

