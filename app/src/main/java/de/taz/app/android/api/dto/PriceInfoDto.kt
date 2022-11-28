package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PriceInfoDto(
    val name: String,
    val price: Int
)
