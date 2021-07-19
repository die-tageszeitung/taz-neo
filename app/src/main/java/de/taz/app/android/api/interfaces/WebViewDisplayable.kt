package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.IssueStub

interface WebViewDisplayable: DownloadableCollection {

    val key: String
    val path: String

    fun previous(): WebViewDisplayable?

    fun next(): WebViewDisplayable?

    fun getIssueStub(): IssueStub?
}
