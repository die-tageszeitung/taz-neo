package de.taz.app.android.api.models

import kotlinx.serialization.Serializable

@Serializable
data class AuthInfo (
    val status: AuthStatus,
    val message: String? = null
)

@Serializable
enum class AuthStatus {
    valid,
    tazIdNotLinked,
    alreadyLinked,
    notValid,
    elapsed,
    notValidMail
}
