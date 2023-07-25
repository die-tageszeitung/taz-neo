package de.taz.app.android.ui.login.passwordCheck


import kotlinx.serialization.Serializable

/**
 * The [PasswordHintResponse] is returned via JSON from the shared javascript password check utility
 * provided via the taz resources.
 * See https://redmine.hal.taz.de/issues/14555 for the initial specification.
 */
@Serializable
data class PasswordHintResponse(val message: String, val strength: Int, val valid: Boolean) {

    val passwordStrengthLevel = when (strength) {
        0 -> PasswordStrengthLevel.NONE
        1 -> PasswordStrengthLevel.LOW
        2 -> PasswordStrengthLevel.MEDIUM
        3 -> PasswordStrengthLevel.HIGH
        else -> PasswordStrengthLevel.NOT_DEFINED
    }
}

enum class PasswordStrengthLevel {
    NONE, LOW, MEDIUM, HIGH, NOT_DEFINED
}