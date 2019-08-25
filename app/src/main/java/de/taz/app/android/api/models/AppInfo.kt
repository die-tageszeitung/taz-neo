package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.persistence.entities.AppInfoEntity

open class AppInfo (
    open val globalBaseUrl: String,
    open val appName: AppName,
    open val appType: AppType
) {

    constructor(productDto: ProductDto) : this(productDto.globalBaseUrl!!, productDto.appName!!, productDto.appType!!)

    fun save(context: Context) {
        val dao = AppDatabase.getInstance(context).appInfoDao()
        dao.insertOrReplace(AppInfoEntity(appName, globalBaseUrl, appType))
    }

    companion object {
        fun get(context: Context): AppInfo {
            return AppDatabase.getInstance(context).appInfoDao().get().toObject()
        }
    }
}

