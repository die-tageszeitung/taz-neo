package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthInfo (
    val status: AuthStatus,
    val message: String? = null
)

@JsonClass(generateAdapter = false)
enum class AuthStatus {
    valid,
    tazIdNotLinked,
    alreadyLinked,
    notValid,
    elapsed
}
