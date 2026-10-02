package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ArticleByMediaSyncId(
    val article: ArticleDto,
    val articleHtml: String? = null,
    val sectionTitle: String? = null,
    val date: String,
    val baseUrl: String? = null,
)
