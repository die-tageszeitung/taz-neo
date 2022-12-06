package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = ImageTypeDtoEnumSerializer::class)
enum class ImageTypeDto {
    button,
    picture,
    advertisement,
    facsimile,
    UNKNOWN
}

object ImageTypeDtoEnumSerializer :
    EnumSerializer<ImageTypeDto>(ImageTypeDto.values(), ImageTypeDto.UNKNOWN)