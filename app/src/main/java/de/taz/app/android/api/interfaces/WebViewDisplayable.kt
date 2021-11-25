package de.taz.app.android.api.interfaces

import android.content.Context
import de.taz.app.android.api.models.IssueStub

interface WebViewDisplayable: DownloadableCollection {

    val key: String
    val path: String

    fun previous(applicationContext: Context): WebViewDisplayable?

    fun next(applicationContext: Context): WebViewDisplayable?

    fun getIssueStub(applicationContext: Context): IssueStub?
}
