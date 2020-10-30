package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.IssueStub
import java.io.File

interface WebViewDisplayable: DownloadableCollection {

    val key: String

    fun getFile(): File?
    fun getFilePath(): String?

    fun previous(): WebViewDisplayable?

    fun next(): WebViewDisplayable?

    fun getIssueStub(): IssueStub?
}
