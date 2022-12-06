package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.CancellationInfoDto
import de.taz.app.android.api.dto.CancellationInfoDto.*
import de.taz.app.android.api.models.CancellationInfo

object CancellationInfoMapper {
    fun from(cancellationInfoDto: CancellationInfoDto): CancellationInfo = when(cancellationInfoDto) {
        aboId -> CancellationInfo.aboId
        tazId -> CancellationInfo.tazId
        noAuthToken -> CancellationInfo.noAuthToken
        elapsed -> CancellationInfo.elapsed
        specialAccess -> CancellationInfo.specialAccess
        UNKNOWN -> CancellationInfo.UNKNOWN_RESPONSE
    }
}
