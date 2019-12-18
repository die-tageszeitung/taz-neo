package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
data class Frame (
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val link: String? = null
)