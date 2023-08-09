package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.SectionTypeDto
import de.taz.app.android.api.dto.SectionTypeDto.UNKNOWN
import de.taz.app.android.api.dto.SectionTypeDto.advertisement
import de.taz.app.android.api.dto.SectionTypeDto.articles
import de.taz.app.android.api.models.SectionType
import de.taz.app.android.util.Log

object SectionTypeMapper {
    fun from(sectionTypeDto: SectionTypeDto): SectionType = when (sectionTypeDto) {
        articles -> SectionType.articles
        advertisement -> SectionType.advertisement
        UNKNOWN -> {
            val hint = "Encountered UNKNOWN SectionTypeDto, falling back to SectionType.articles"
            Log(this::class.java.name).warn(hint)
            SectionType.articles
        }
    }
}