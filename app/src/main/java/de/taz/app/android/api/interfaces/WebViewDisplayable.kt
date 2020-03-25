package de.taz.app.android.api.interfaces

import java.io.File

interface WebViewDisplayable: CacheableDownload {

    val key: String

    fun getFile(): File?

    fun previous(): WebViewDisplayable?

    fun next(): WebViewDisplayable?

}