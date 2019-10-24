package de.taz.app.android.api.interfaces

import java.io.File

interface WebViewDisplayable {

    fun getFile(): File?

    fun previous(): WebViewDisplayable?

    fun next(): WebViewDisplayable?

}