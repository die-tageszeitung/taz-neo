package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.ObservableDownload

const val APP_INFO_TAG = "appInfo"

class AppInfoKey: ObservableDownload {
    override fun getDownloadTag(): String {
        return APP_INFO_TAG
    }
}

@Entity(tableName = "AppInfo")
data class AppInfo (
    @PrimaryKey val appName: AppName,
    val globalBaseUrl: String,
    val appType: AppType,
    val androidVersion: Int
): ObservableDownload {

    override fun getDownloadTag(): String {
        return APP_INFO_TAG
    }
}
