package de.taz.app.android.api.models

data class AuthInfo(
    val status: AuthStatus,
    val message: String? = null,
    val loginWeek: Boolean? = null,
)

enum class AuthStatus {
    valid,
    tazIdNotLinked,
    alreadyLinked,
    notValid,
    elapsed,
    notValidMail
}
