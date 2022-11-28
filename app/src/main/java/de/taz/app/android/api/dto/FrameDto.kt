package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FrameDto (
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val link: String? = null
)