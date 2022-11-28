package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
data class CancellationVariables(
    val isForce: Boolean? = false
) : Variables