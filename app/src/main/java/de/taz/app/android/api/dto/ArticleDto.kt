package de.taz.app.android.api.dto

import de.taz.app.android.api.models.ArticleType
import kotlinx.serialization.Serializable

@Serializable
data class ArticleDto (
    val articleHtml: FileEntryDto,
    val title: String? = null,
    val teaser: String? = null,
    val onlineLink: String? = null,
    val audio: AudioDto? = null,
    val pageNameList: List<String>? = null,
    val imageList: List<ImageDto>? = null,
    val authorList: List<AuthorDto>? = null,
    val mediaSyncId: Int? = null,
    val chars: Int? = null,
    val words: Int? = null,
    val readMinutes: Int? = null,
    val articleType: String? = null,
    val pdf: FileEntryDto? = null,
    val iconList: List<ImageDto>? = null,
)