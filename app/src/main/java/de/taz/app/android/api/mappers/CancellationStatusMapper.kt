package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.CancellationStatusDto
import de.taz.app.android.api.models.CancellationStatus

object CancellationStatusMapper {
    fun from(cancellationStatusDto: CancellationStatusDto): CancellationStatus {
        return CancellationStatus(
            cancellationStatusDto.tazIdMail,
            cancellationStatusDto.cancellationLink,
            cancellationStatusDto.canceled,
            CancellationInfoMapper.from(cancellationStatusDto.info)
        )
    }
}