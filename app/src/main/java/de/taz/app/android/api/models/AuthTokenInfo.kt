package de.taz.app.android.api.models

import kotlinx.serialization.Serializable

@Serializable
data class AuthTokenInfo (
    var token: String? = null,
    var authInfo: AuthInfo
)
