package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DataDto (
    val product: ProductDto? = null,
    val authentificationToken: AuthTokenInfoDto? = null,
    val downloadStop: Boolean? = null,
    val downloadStart: String? = null
)