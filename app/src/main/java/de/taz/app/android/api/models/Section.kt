package de.taz.app.android.api.models

data class Section (
    val sectionHtml: FileEntry,
    val title: String,
    val type: SectionType,
    val articleList: List<Article>? = null,
    val imageList: List<Image>? = null
)

enum class SectionType {
    articles
}