package de.taz.app.android.api.variables

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import de.taz.app.android.util.Json

@Serializable
data class IssueVariables(
    @Required val feedName: String? = null,
    @Required val issueDate: String? = null,
    @Required val limit: Int = 1
): Variables