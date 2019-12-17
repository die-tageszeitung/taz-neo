package de.taz.app.android.api.dto

import de.taz.app.android.api.models.Frame
import de.taz.app.android.api.models.PageType

data class PageDto(
    val pagePdf: FileEntryDto,
    val title: String? = null,
    val pagina: String? = null,
    val type: PageType? = null,
    val frameList: List<Frame>? = null
)
