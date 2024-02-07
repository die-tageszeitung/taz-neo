package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.withTransaction
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.util.SingletonHolder


class AppInfoRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<AppInfoRepository, Context>(::AppInfoRepository)

    /**
     * Save the full downloaded [AppInfo] metadata to the database
     * and replace any existing [AppInfo] with the same key.
     */
    suspend fun save(appInfo: AppInfo): AppInfo {
        return appDatabase.withTransaction {
            appDatabase.appInfoDao().insertOrReplace(appInfo)
            requireNotNull(get()) { "Could not get $appInfo after it was saved" }
        }
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