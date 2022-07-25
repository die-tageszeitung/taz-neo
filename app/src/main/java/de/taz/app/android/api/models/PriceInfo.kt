package de.taz.app.android.api.models

import kotlinx.serialization.Serializable

@Serializable
data class PriceInfo(
    val name: String,
    val price: Int
)
