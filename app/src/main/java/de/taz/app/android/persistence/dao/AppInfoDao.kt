package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.TypeConverter
import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import de.taz.app.android.persistence.BaseDao
import de.taz.app.android.api.models.AppInfo

@Dao
abstract class AppInfoDao : BaseDao<AppInfo>() {
    @Query("SELECT * FROM AppInfo LIMIT 1")
    abstract fun get(): AppInfo
}

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
