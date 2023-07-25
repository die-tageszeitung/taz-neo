package de.taz.app.android.ui.login.passwordCheck

import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import de.taz.app.android.R

/**
 * Show the [PasswordHintResponse] message on the passwords input layout.
 * If the password is invalid, an error message is shown and the field is marked with the error color.
 * Otherwise a helper text is shown with the according password hint message.
 */
fun TextInputLayout.setPasswordHintResponse(
    passwordHintResponse: PasswordHintResponse,
) {
    val colorId = when (passwordHintResponse.passwordStrengthLevel) {
        PasswordStrengthLevel.NONE -> R.color.error
        PasswordStrengthLevel.LOW -> R.color.password_hint_strength_low
        PasswordStrengthLevel.MEDIUM, PasswordStrengthLevel.HIGH -> R.color.password_hint_strength_medium
        PasswordStrengthLevel.NOT_DEFINED -> R.color.textColorAccent
    }

    if (passwordHintResponse.valid) {
        setHelperTextColor(
            ContextCompat.getColorStateList(context, colorId)
        )
        error = null
        helperText = passwordHintResponse.message
    } else {
        helperText = null
        error = passwordHintResponse.message
    }
}