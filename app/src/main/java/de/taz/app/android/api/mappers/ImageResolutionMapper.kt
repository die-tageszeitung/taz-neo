package de.taz.app.android.api.mappers

import android.util.Log
import de.taz.app.android.api.dto.ImageResolutionDto
import de.taz.app.android.api.dto.ImageResolutionDto.*
import de.taz.app.android.api.models.ImageResolution
import io.sentry.Sentry

object ImageResolutionMapper {
    fun from(imageResolutionDto: ImageResolutionDto): ImageResolution = when (imageResolutionDto) {
        small -> ImageResolution.small
        normal -> ImageResolution.normal
        high -> ImageResolution.high
        UNKNOWN -> {
            val hint =
                "Encountered UNKNOWN ImageResolutionMapper, falling back to ImageResolutionMapper.normal"
            Log.w(ImageResolutionMapper::class.java.name, hint)
            Sentry.captureMessage(hint)
            ImageResolution.normal
        }
    }
}