package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthorDto(
    val name: String? = null,
    val imageAuthor: FileEntryDto? = null
)