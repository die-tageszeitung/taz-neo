package de.taz.app.android.api.mappers

import android.util.Log
import de.taz.app.android.api.dto.ImageTypeDto
import de.taz.app.android.api.dto.ImageTypeDto.*
import de.taz.app.android.api.models.ImageType
import io.sentry.Sentry

object ImageTypeMapper {
    fun from(imageTypeDto: ImageTypeDto): ImageType = when (imageTypeDto) {
        button -> ImageType.button
        picture -> ImageType.picture
        advertisement -> ImageType.advertisement
        facsimile -> ImageType.facsimile
        UNKNOWN -> {
            val hint = "Encountered UNKNOWN ImageTypeDto, falling back to ImageType.picture"
            Log.w(ImageTypeMapper::class.java.name, hint)
            Sentry.captureMessage(hint)
            ImageType.picture
        }
    }
}