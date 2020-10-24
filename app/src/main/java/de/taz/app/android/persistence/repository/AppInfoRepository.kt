package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.util.SingletonHolder

@Mockable
class AppInfoRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<AppInfoRepository, Context>(::AppInfoRepository)

    fun save(appInfo: AppInfo) {
        appDatabase.appInfoDao().insertOrReplace(appInfo)
    }

    fun get(): AppInfo? {
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

    fun getCount(): Int {
        return appDatabase.appInfoDao().count()
    }

}