package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import de.taz.app.android.util.Json

@Serializable
data class FeedVariables(
        val feedName: String? = null
) : Variables