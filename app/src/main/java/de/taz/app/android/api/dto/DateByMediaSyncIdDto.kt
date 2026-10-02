package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class DateByMediaSyncIdDto(
    val mediaSyncId: Int,
    val date: String,
)