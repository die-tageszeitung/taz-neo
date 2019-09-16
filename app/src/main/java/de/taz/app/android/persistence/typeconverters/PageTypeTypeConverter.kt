package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.models.PageType

class PageTypeTypeConverter {

    @TypeConverter
    fun toString(pageType: PageType): String {
        return pageType.name
    }

    @TypeConverter
    fun toPageType(value: String): PageType {
        return PageType.valueOf(value)
    }


}