package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.util.SingletonHolder

class AppInfoRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<AppInfoRepository, Context>(::AppInfoRepository)

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
                appInfoEntity.appType,
                appInfoEntity.androidVersion
            )
        }
    }

    fun getCount(): Int {
        return appDatabase.appInfoDao().count()
    }

}