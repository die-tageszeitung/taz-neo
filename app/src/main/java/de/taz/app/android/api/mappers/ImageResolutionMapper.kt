package de.taz.app.android.api.mappers

import android.util.Log
import de.taz.app.android.api.dto.ImageResolutionDto
import de.taz.app.android.api.dto.ImageResolutionDto.UNKNOWN
import de.taz.app.android.api.dto.ImageResolutionDto.high
import de.taz.app.android.api.dto.ImageResolutionDto.normal
import de.taz.app.android.api.dto.ImageResolutionDto.small
import de.taz.app.android.api.models.ImageResolution

object ImageResolutionMapper {
    fun from(imageResolutionDto: ImageResolutionDto): ImageResolution = when (imageResolutionDto) {
        small -> ImageResolution.small
        normal -> ImageResolution.normal
        high -> ImageResolution.high
        UNKNOWN -> {
            val hint =
                "Encountered UNKNOWN ImageResolutionMapper, falling back to ImageResolutionMapper.normal"
            Log.w(ImageResolutionMapper::class.java.name, hint)
            ImageResolution.normal
        }
    }
}