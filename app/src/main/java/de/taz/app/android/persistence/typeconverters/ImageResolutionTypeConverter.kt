package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.models.ImageResolution


class ImageResolutionTypeConverter {
    @TypeConverter
    fun toString(imageResolution: ImageResolution): String {
        return imageResolution.name
    }
    @TypeConverter
    fun toImageResolutionEnum(value: String): ImageResolution {
        return ImageResolution.valueOf(value)
    }
}
