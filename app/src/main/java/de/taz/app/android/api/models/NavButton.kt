package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
data class NavButton (
    val name: String,
    val alpha: Float? = null
)
