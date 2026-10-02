package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
enum class ArticleOrder { none, dateDesc, dateAsc, list }