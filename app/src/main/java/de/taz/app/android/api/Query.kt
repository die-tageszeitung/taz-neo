package de.taz.app.android.api

import androidx.annotation.VisibleForTesting
import de.taz.app.android.api.variables.Variables
import kotlinx.serialization.Serializable

@Serializable
data class Query(
    @get:VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) val query: String,
    var variables: Variables? = null
)