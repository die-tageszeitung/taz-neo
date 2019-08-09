package de.taz.app.android.api.models

data class AuthInfo (
    val status: AuthStatus,
    val message: String? = null
)

enum class AuthStatus {
    valid,
    notValid,
    elapsed
}
