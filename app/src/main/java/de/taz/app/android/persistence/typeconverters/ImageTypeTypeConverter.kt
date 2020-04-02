package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.models.ImageType


class ImageTypeTypeConverter {
    @TypeConverter
    fun toString(imageType: ImageType): String {
        return imageType.name
    }
    @TypeConverter
    fun toImageTypeEnum(value: String): ImageType {
        return ImageType.valueOf(value)
    }
}
