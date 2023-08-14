package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.util.SingletonHolder


class AppInfoRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<AppInfoRepository, Context>(::AppInfoRepository)

    suspend fun save(appInfo: AppInfo): AppInfo {
        appDatabase.appInfoDao().insertOrReplace(appInfo)
        return get()!!
    }

    suspend fun get(): AppInfo? {
        val appInfoEntity = appDatabase.appInfoDao().get()
        return appInfoEntity?.let {
            AppInfo(
                it.appName,
                it.globalBaseUrl,
                it.appType,
                it.androidVersion
            )
        }
    }

    suspend fun getCount(): Int {
        return appDatabase.appInfoDao().count()
    }

}