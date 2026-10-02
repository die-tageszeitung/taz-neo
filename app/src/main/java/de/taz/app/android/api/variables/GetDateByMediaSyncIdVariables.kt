package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
data class GetDateByMediaSyncIdVariables(
    val mediaSyncIds: String,
): Variables