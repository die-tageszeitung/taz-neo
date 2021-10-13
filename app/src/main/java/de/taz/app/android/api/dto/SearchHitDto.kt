package de.taz.app.android.api.dto

data class SearchHitDto(
    val article: ArticleDto?,
    val baseUrl: String,
    val snippet: String?,
    val title: String,
    val teaser: String?,
    val sectionTitle: String?,
    val date: String,
    val articleHtml: String?
)