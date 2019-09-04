package de.taz.app.android.persistence.repository

import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.persistence.AppDatabase

class AppInfoRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance()) {

    fun save(appInfo: AppInfo) {
        appDatabase.appInfoDao().insertOrReplace(appInfo)
    }

    fun get(): AppInfo {
        val appInfoEntity = appDatabase.appInfoDao().get()
        return AppInfo(appInfoEntity.appName, appInfoEntity.globalBaseUrl, appInfoEntity.appType)
    }

    fun getCount(): Int {
        return appDatabase.appInfoDao().count()
    }

}