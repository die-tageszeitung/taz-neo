package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.PageTypeDto
import de.taz.app.android.api.dto.PageTypeDto.UNKNOWN
import de.taz.app.android.api.dto.PageTypeDto.left
import de.taz.app.android.api.dto.PageTypeDto.panorama
import de.taz.app.android.api.dto.PageTypeDto.right
import de.taz.app.android.api.models.PageType
import de.taz.app.android.util.Log

object PageTypeMapper {
    fun from(pageTypeDto: PageTypeDto): PageType = when (pageTypeDto) {
        left -> PageType.left
        right -> PageType.right
        panorama -> PageType.panorama
        UNKNOWN -> {
            val hint = "Encountered UNKNOWN PageTypeDto, falling back to PageType.left"
            Log(this::class.java.name).warn(hint)
            PageType.left
        }
    }
}