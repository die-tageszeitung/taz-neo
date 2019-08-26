package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.dto.AppType


class AppTypeConverter {
    @TypeConverter
    fun toString(appType: AppType): String {
        return appType.name
    }
    @TypeConverter
    fun toAppTypeEnum(value: String): AppType {
        return AppType.valueOf(value)
    }
}
