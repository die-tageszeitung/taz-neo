package de.taz.app.android.api.variables

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import de.taz.app.android.util.Json

@Serializable
data class CancellationVariables(
    @Required val isForce: Boolean? = false
) : Variables