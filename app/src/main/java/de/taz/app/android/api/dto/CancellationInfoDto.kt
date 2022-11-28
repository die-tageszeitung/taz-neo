package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = CancellationInfoDtoEnumSerializer::class)
enum class CancellationInfoDto {
    aboId,
    tazId,
    noAuthToken,
    elapsed,
    specialAccess,
    UNKNOWN
}

object CancellationInfoDtoEnumSerializer :
    EnumSerializer<CancellationInfoDto>(CancellationInfoDto.values(), CancellationInfoDto.UNKNOWN)