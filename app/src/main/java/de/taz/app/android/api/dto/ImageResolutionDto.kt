package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = ImageResolutionDtoEnumSerializer::class)
enum class ImageResolutionDto {
    small,
    normal,
    high,
    UNKNOWN
}


object ImageResolutionDtoEnumSerializer :
    EnumSerializer<ImageResolutionDto>(ImageResolutionDto.values(), ImageResolutionDto.UNKNOWN)