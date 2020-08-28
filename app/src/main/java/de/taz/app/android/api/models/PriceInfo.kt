package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PriceInfo(
    val name: String,
    val price: Int
)
