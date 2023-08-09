package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.AuthStatusDto
import de.taz.app.android.api.dto.AuthStatusDto.UNKNOWN
import de.taz.app.android.api.dto.AuthStatusDto.alreadyLinked
import de.taz.app.android.api.dto.AuthStatusDto.elapsed
import de.taz.app.android.api.dto.AuthStatusDto.notValid
import de.taz.app.android.api.dto.AuthStatusDto.notValidMail
import de.taz.app.android.api.dto.AuthStatusDto.tazIdNotLinked
import de.taz.app.android.api.dto.AuthStatusDto.valid
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.util.Log

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
                Log(this::class.java.name).warn(hint)
                AuthStatus.notValid
            }
        }
    }
}