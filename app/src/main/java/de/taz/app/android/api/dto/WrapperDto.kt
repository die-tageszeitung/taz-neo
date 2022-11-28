package de.taz.app.android.api.dto

import de.taz.app.android.annotation.Mockable
import kotlinx.serialization.Serializable

@Serializable
@Mockable
data class WrapperDto (
    val data: DataDto? = null,
    val errors: List<ErrorDto> = emptyList()
)