package de.taz.app.android.persistence.entities

import androidx.room.*
import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import de.taz.app.android.api.models.AppInfo

@Entity(tableName = "AppInfo")
class AppInfoEntity (
    @PrimaryKey val appName: AppName,
    val globalBaseUrl: String,
    val appType: AppType
): GenericEntity<AppInfo>() {
    override fun toObject(): AppInfo {
        return AppInfo(globalBaseUrl, appName, appType)
    }
}

