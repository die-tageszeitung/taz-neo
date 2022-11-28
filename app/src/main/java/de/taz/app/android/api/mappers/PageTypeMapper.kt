package de.taz.app.android.api.mappers

import android.util.Log
import de.taz.app.android.api.dto.PageTypeDto
import de.taz.app.android.api.dto.PageTypeDto.*
import de.taz.app.android.api.models.PageType
import io.sentry.Sentry

object PageTypeMapper {
    fun from(pageTypeDto: PageTypeDto): PageType = when (pageTypeDto) {
        left -> PageType.left
        right -> PageType.right
        panorama -> PageType.panorama
        UNKNOWN -> {
            val hint = "Encountered UNKNOWN PageTypeDto, falling back to PageType.left"
            Log.w(PageTypeMapper::class.java.name, hint)
            Sentry.captureMessage(hint)
            PageType.left
        }
    }
}