package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = AppTypeDtoEnumSerializer::class)
enum class AppTypeDto { production, test, local, UNKNOWN }

object AppTypeDtoEnumSerializer :
    EnumSerializer<AppTypeDto>(AppTypeDto.values(), AppTypeDto.UNKNOWN)