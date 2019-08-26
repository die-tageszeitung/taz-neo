package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.dto.AppName


class AppNameConverter {
    @TypeConverter
    fun toString(appName: AppName): String {
        return appName.name
    }
    @TypeConverter
    fun toAppNameEnum(value: String): AppName {
        return AppName.valueOf(value)
    }
}