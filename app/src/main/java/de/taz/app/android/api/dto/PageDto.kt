package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.models.Frame
import de.taz.app.android.api.models.PageType

@JsonClass(generateAdapter = true)
data class PageDto(
    val pagePdf: FileEntryDto,
    val title: String? = null,
    val pagina: String? = null,
    val type: PageType? = null,
    val frameList: List<Frame>? = null
)
