package de.taz.app.android.api

import de.taz.app.android.api.variables.Variables
import kotlinx.serialization.Serializable

@Serializable
data class Query(
    private val query: String,
    var variables: Variables? = null
)