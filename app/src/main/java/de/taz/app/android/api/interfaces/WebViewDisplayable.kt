package de.taz.app.android.api.interfaces

import android.content.Context
import de.taz.app.android.api.models.IssueStub

interface WebViewDisplayable: DownloadableCollection {

    val key: String

    suspend fun previous(applicationContext: Context): String?

    suspend fun next(applicationContext: Context): String?

    suspend fun getIssueStub(applicationContext: Context): IssueStub?
}
