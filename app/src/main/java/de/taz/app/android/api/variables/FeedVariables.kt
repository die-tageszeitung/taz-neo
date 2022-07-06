package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FeedVariables(
        val feedName: String? = null
) : Variables {
    override fun toJson(): String = Json.encodeToString(this)
}
