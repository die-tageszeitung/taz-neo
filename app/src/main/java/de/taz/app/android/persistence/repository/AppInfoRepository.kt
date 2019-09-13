package de.taz.app.android.persistence.repository

import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.persistence.AppDatabase

class AppInfoRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance()) {

    fun save(appInfo: AppInfo) {
        appDatabase.appInfoDao().insertOrReplace(appInfo)
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(): AppInfo {
        return get() ?: throw NotFoundException()
    }

    fun get(): AppInfo? {
        return appDatabase.appInfoDao().get()?.let { appInfoEntity ->
            AppInfo(
                appInfoEntity.appName,
                appInfoEntity.globalBaseUrl,
                appInfoEntity.appType
            )
        }
    }

    fun getCount(): Int {
        return appDatabase.appInfoDao().count()
    }

}