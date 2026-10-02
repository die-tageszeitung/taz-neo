package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class GetDateByMediaSyncId(
    val dateList: List<DateByMediaSyncIdDto>
)
