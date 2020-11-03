package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.models.FileEntry

@JsonClass(generateAdapter = true)
data class MomentDto(
    val imageList: List<ImageDto>? = null,
    val creditList: List<ImageDto>? = null,
    val momentList: List<FileEntryDto>? = null
)