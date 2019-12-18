package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthTokenInfo (
    var token: String? = null,
    var authInfo: AuthInfo
)
