package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable
import de.taz.app.android.annotation.Mockable

@Serializable
@Mockable
data class WrapperDto (
    val data: DataDto? = null,
    val errors: List<ErrorDto> = emptyList()
)