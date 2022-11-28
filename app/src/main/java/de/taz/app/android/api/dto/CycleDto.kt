package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = CycleDtoEnumSerializer::class)
enum class CycleDto {
    daily,
    weekly,
    monthly,
    quarterly,
    yearly,
    UNKNOWN
}

object CycleDtoEnumSerializer : EnumSerializer<CycleDto>(CycleDto.values(), CycleDto.UNKNOWN)