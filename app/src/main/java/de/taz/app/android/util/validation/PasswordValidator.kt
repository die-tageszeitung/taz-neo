package de.taz.app.android.util.validation

import java.util.regex.Pattern

// regex for password 8 chars, 1 letter, 1 digit, 1 special char
private const val PASSWORD_PATTERN = """^.*(?=.{8,})(?=.*[a-zA-Z])(?=.*\d)(?=.*[\W]).*$"""

class PasswordValidator {
    private val pattern = Pattern.compile(PASSWORD_PATTERN)

    operator fun invoke(password: String): Boolean {
        return pattern.matcher(password).matches()
    }
}