package de.taz.app.android.api.mappers

import android.util.Log
import de.taz.app.android.api.dto.SectionTypeDto
import de.taz.app.android.api.dto.SectionTypeDto.UNKNOWN
import de.taz.app.android.api.dto.SectionTypeDto.articles
import de.taz.app.android.api.models.SectionType
import io.sentry.Sentry

object SectionTypeMapper {
    fun from(sectionTypeDto: SectionTypeDto): SectionType = when (sectionTypeDto) {
        articles -> SectionType.articles
        UNKNOWN -> {
            val hint = "Encountered UNKNOWN SectionTypeDto, falling back to SectionType.articles"
            Log.w(SectionTypeMapper::class.java.name, hint)
            Sentry.captureMessage(hint)
            SectionType.articles
        }
    }
}