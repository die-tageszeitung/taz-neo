package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class IssueVariables(
    val feedName: String? = null,
    val issueDate: String? = null,
    val limit: Int = 1
): Variables {
    override fun toJson(): String = Json.encodeToString(this)
}
