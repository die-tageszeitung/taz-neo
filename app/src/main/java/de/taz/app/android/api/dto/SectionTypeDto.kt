package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = SectionTypeDtoEnumSerializer::class)
enum class SectionTypeDto {
    articles,
    UNKNOWN
}

object SectionTypeDtoEnumSerializer :
    EnumSerializer<SectionTypeDto>(SectionTypeDto.values(), SectionTypeDto.UNKNOWN)