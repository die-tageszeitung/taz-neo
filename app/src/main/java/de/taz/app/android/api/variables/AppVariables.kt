package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
data class AppVariables(val os: String, val type: String) : Variables