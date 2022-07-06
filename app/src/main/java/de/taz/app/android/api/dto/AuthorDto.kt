package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable


@Serializable
data class AuthorDto(
    val name: String? = null,
    val imageAuthor: FileEntryDto? = null
)