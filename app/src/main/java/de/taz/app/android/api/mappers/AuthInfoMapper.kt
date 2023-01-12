package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.AuthInfoDto
import de.taz.app.android.api.models.AuthInfo

object AuthInfoMapper {
    fun from(authInfoDto: AuthInfoDto): AuthInfo = AuthInfo(
        AuthStatusMapper.from(authInfoDto.status),
        authInfoDto.message,
        authInfoDto.loginWeek,
    )
}