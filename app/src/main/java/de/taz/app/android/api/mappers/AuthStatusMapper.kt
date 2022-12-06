package de.taz.app.android.api.mappers

import android.util.Log
import de.taz.app.android.api.dto.AuthStatusDto
import de.taz.app.android.api.dto.AuthStatusDto.*
import de.taz.app.android.api.models.AuthStatus
import io.sentry.Sentry

object AuthStatusMapper {
    fun from(authStatusDto: AuthStatusDto): AuthStatus {
        return when (authStatusDto) {
            valid -> AuthStatus.valid
            tazIdNotLinked -> AuthStatus.tazIdNotLinked
            alreadyLinked -> AuthStatus.alreadyLinked
            notValid -> AuthStatus.notValid
            elapsed -> AuthStatus.elapsed
            notValidMail -> AuthStatus.notValidMail
            UNKNOWN -> {
                val hint = "Encountered UNKNOWN AuthStatusDto, falling back to AuthStatus.notValid"
                Log.w(AuthStatusMapper::class.java.name, hint)
                Sentry.captureMessage(hint)
                AuthStatus.notValid
            }
        }
    }
}