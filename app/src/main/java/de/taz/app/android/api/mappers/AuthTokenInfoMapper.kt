package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.AuthTokenInfoDto
import de.taz.app.android.api.models.AuthTokenInfo

object AuthTokenInfoMapper {
    fun from(authTokenInfoDto: AuthTokenInfoDto): AuthTokenInfo = AuthTokenInfo(
        authTokenInfoDto.token,
        AuthInfoMapper.from(authTokenInfoDto.authInfo),
        authTokenInfoDto.customerType?.let { CustomerTypeMapper.from(it) } // FIXME: might be non nullable, as we have the value unknown
    )
}