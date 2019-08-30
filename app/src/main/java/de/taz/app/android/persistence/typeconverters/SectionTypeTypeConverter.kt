package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.models.SectionType

class SectionTypeTypeConverter {

    @TypeConverter
    fun toString(sectionType: SectionType): String {
        return sectionType.name
    }

    @TypeConverter
    fun toPageType(value: String): SectionType {
        return SectionType.valueOf(value)
    }


}