package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.util.SingletonHolder

class AppInfoRepository private constructor(applicationContext: Context) {
    companion object : SingletonHolder<AppInfoRepository, Context>(::AppInfoRepository)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var appDatabase = AppDatabase.getInstance(applicationContext)

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