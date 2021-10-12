package de.taz.app.android.data

import android.content.Context
import de.taz.app.android.api.ApiService
import de.taz.app.android.util.SingletonHolder

class FullTextSearchService(applicationContext: Context) {
    companion object : SingletonHolder<FullTextSearchService, Context>(::FullTextSearchService)
    private val apiService = ApiService.getInstance(applicationContext)

    fun search() {

    }


}