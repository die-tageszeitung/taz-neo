package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
data class IssueVariables(
    val feedName: String? = null,
    val issueDate: String? = null,
    val limit: Int = 1
): Variables