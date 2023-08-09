package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AppDto(val minVersion: String? = null)