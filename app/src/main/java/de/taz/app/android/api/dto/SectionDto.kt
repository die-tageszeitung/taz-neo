package de.taz.app.android.api.dto

data class SectionDto (
    val sectionHtml: FileEntryDto,
    val title: String,
    val type: SectionType,
    val articleList: List<ArticleDto>? = null,
    val imageList: List<FileEntryDto>? = null
)

enum class SectionType {
    articles
}