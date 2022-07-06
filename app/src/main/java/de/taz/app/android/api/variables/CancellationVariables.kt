package de.taz.app.android.api.variables

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CancellationVariables(
    @Required val isForce: Boolean? = false
) : Variables {
    override fun toJson(): String = Json.encodeToString(this)
}
