package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SectionDto (
    val sectionHtml: FileEntryDto,
    val title: String,
    val type: SectionType,
    val articleList: List<ArticleDto>? = null,
    val imageList: List<ImageDto>? = null,
    val extendedTitle: String? = null,
    val navButton: ImageDto
)

@Serializable
enum class SectionType {
    articles
}