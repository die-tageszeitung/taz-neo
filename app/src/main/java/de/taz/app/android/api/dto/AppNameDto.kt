package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = AppNameDtoEnumSerializer::class)
enum class AppNameDto { taz, LMd, UNKNOWN }

object AppNameDtoEnumSerializer :
    EnumSerializer<AppNameDto>(AppNameDto.values(), AppNameDto.UNKNOWN)