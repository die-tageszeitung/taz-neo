package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthTokenInfoDto(
    var token: String? = null,
    var authInfo: AuthInfoDto,
    var customerType: CustomerTypeDto? = null,
)
