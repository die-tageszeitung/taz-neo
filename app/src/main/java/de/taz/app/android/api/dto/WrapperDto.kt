package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass
import de.taz.app.android.annotation.Mockable

@JsonClass(generateAdapter = true)
@Mockable
data class WrapperDto (
    val data: DataDto? = null,
    val errors: List<ErrorDto> = emptyList()
)