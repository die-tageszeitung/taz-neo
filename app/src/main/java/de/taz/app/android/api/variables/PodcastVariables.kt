package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
data class PodcastVariables(
    val limit: Int = 1
): Variables