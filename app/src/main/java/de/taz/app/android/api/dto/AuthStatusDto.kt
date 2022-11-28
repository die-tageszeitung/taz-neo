package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = AuthStatusDtoEnumSerializer::class)
enum class AuthStatusDto {
    valid,
    tazIdNotLinked,
    alreadyLinked,
    notValid,
    elapsed,
    notValidMail,
    UNKNOWN
}

object AuthStatusDtoEnumSerializer :
    EnumSerializer<AuthStatusDto>(AuthStatusDto.values(), AuthStatusDto.UNKNOWN)