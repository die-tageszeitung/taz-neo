package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
data class GetArticlesByMediaSyncIdVariables(
    val mediaSyncIds: String,
    val articleOrder: ArticleOrder,
): Variables