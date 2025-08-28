package de.taz.app.android.api.models

import de.taz.app.android.api.interfaces.AudioPlayerPlayable

data class SearchHit(
    val articleFileName: String,
    val mediaSyncId: Int?,
    val authorList: List<Author>,
    val onlineLink: String?,
    val baseUrl: String,
    val snippet: String?,
    val title: String,
    val teaser: String?,
    val sectionTitle: String?,
    val date: String,
    val articleHtml: String?,
    val articlePdfFileName: String?,
    val audioFileName: String?,
): AudioPlayerPlayable {
    override val audioPlayerPlayableKey: String
        get() = articleFileName

    fun getAuthorNames(): String {
        return if (authorList.isNotEmpty()) {
            authorList.map { it.name }.distinct().joinToString(", ")
        } else {
            ""
        }
    }
}
