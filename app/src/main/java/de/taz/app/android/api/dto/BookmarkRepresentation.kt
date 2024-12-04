package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
class BookmarkRepresentation(
    val mediaSyncId: Int,
    val date: String,
)