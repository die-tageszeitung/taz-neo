package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.ImageTypeDto
import de.taz.app.android.api.dto.ImageTypeDto.UNKNOWN
import de.taz.app.android.api.dto.ImageTypeDto.advertisement
import de.taz.app.android.api.dto.ImageTypeDto.button
import de.taz.app.android.api.dto.ImageTypeDto.facsimile
import de.taz.app.android.api.dto.ImageTypeDto.picture
import de.taz.app.android.api.models.ImageType
import de.taz.app.android.util.Log

object ImageTypeMapper {
    fun from(imageTypeDto: ImageTypeDto): ImageType = when (imageTypeDto) {
        button -> ImageType.button
        picture -> ImageType.picture
        advertisement -> ImageType.advertisement
        facsimile -> ImageType.facsimile
        UNKNOWN -> {
            val hint = "Encountered UNKNOWN ImageTypeDto, falling back to ImageType.picture"
            Log(this::class.java.name).warn(hint)
            ImageType.picture
        }
    }
}