package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = PageTypeDtoEnumSerializer::class)
enum class PageTypeDto {
    left,
    right,
    panorama,
    UNKNOWN
}

object PageTypeDtoEnumSerializer :
    EnumSerializer<PageTypeDto>(PageTypeDto.values(), PageTypeDto.UNKNOWN)