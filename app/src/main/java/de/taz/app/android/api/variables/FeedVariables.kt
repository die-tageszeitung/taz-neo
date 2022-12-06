package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
data class FeedVariables(
        val feedName: String? = null
) : Variables