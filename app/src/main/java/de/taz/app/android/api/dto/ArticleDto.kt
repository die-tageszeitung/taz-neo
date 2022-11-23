package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ArticleDto (
    val articleHtml: FileEntryDto,
    val title: String? = null,
    val teaser: String? = null,
    val onlineLink: String? = null,
    val audioFile: FileEntryDto? = null,
    val pageNameList: List<String>? = null,
    val imageList: List<ImageDto>? = null,
    val authorList: List<AuthorDto>? = null,
    val mediaSyncId: Int? = null,
    val chars: Int? = null,
    val words: Int? = null,
    val readMinutes: Int? = null,
)