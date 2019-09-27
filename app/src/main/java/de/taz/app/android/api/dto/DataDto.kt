package de.taz.app.android.api.dto

data class DataDto (
    val product: ProductDto? = null,
    val authentificationToken: AuthTokenInfoDto? = null,
    val downloadStop: Boolean? = null,
    val downloadStart: String? = null
)