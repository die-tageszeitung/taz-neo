package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.FrameDto
import de.taz.app.android.api.models.Frame

object FrameMapper {
    fun from(frameDto: FrameDto): Frame {
        return Frame (
            frameDto.x1,
            frameDto.y1,
            frameDto.x2,
            frameDto.y2,
            frameDto.link
        )
    }
}