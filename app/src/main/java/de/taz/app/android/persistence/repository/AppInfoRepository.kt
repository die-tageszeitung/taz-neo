package de.taz.app.android.persistence.repository

import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.persistence.AppDatabase

class AppInfoRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance()) {

    private val appInfoDao = appDatabase.appInfoDao()

    fun save(appInfo: AppInfo) {
        appInfoDao.insertOrReplace(appInfo)
    }

    fun get(): AppInfo {
        val appInfoEntity = appInfoDao.get()
        return AppInfo(appInfoEntity.appName, appInfoEntity.globalBaseUrl, appInfoEntity.appType)
    }

    fun getCount(): Int {
        return appInfoDao.count()
    }

}