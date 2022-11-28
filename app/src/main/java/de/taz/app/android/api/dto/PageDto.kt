package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PageDto(
    val pagePdf: FileEntryDto,
    val title: String? = null,
    val pagina: String? = null,
    val type: PageTypeDto? = null,
    val frameList: List<FrameDto>? = null,
    val baseUrl: String? = null
)
