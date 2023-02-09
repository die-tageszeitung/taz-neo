package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthInfoDto (
    val status: AuthStatusDto,
    val message: String? = null,
    val loginWeek: Boolean? = null,
)