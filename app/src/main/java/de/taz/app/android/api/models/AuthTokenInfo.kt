package de.taz.app.android.api.models

import de.taz.app.android.api.dto.CustomerType
import kotlinx.serialization.Serializable

@Serializable
data class AuthTokenInfo (
    var token: String? = null,
    var authInfo: AuthInfo,
    var customerType: CustomerType? = null,
)
