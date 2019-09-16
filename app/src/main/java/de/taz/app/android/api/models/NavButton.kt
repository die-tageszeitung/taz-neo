package de.taz.app.android.api.models

import kotlinx.serialization.Serializable

@Serializable
data class NavButton (
    val name: String,
    val alpha: Float? = null
)
