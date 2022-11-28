package de.taz.app.android.api.models

data class SearchHit(
    val articleFileName: String,
    val authorList: List<Author>,
    val onlineLink: String?,
    val baseUrl: String,
    val snippet: String?,
    val title: String,
    val teaser: String?,
    val sectionTitle: String?,
    val date: String,
    val articleHtml: String?,
)