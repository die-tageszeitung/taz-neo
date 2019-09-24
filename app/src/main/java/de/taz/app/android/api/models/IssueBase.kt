package de.taz.app.android.api.models

import androidx.lifecycle.LiveData
import androidx.room.Entity
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.IssueRepository

@Entity(
    tableName = "Issue",
    primaryKeys = ["feedName", "date"]
)
open class IssueBase(
    open val feedName: String,
    open val date: String,
    open val key: String? = null,
    open val baseUrl: String,
    open val status: IssueStatus,
    open val minResourceVersion: Int,
    open val zipName: String? = null,
    open val zipPdfName: String? = null,
    open val navButton: NavButton? = null,
    open val fileList: List<String>,
    open val fileListPdf: List<String> = emptyList()
) {

    constructor(issue: Issue): this (
        issue.feedName, issue.date, issue.key, issue.baseUrl, issue.status,
        issue.minResourceVersion, issue.zipName, issue.zipPdfName,
        issue.navButton, issue.fileList, issue.fileListPdf
    )

    val tag: String
        get() = "$feedName/$date"

    val issueFileList: List<String>
        get() = fileList.filter { !it.startsWith("/global/") }

    val globalFileList: List<String>
        get() = fileList.filter { it.startsWith("/global/") }.map { it.split("/").last() }

    fun isDownloadedLiveData(): LiveData<Boolean> {
        return DownloadRepository.getInstance().isDownloadedLiveData(getAllFileNames())
    }


    fun isDownloaded(): Boolean {
        return DownloadRepository.getInstance().isDownloaded(getAllFileNames())
    }

    private fun getAllFileNames(): List<String> {
        return listOf(issueFileList, globalFileList).flatten()
    }

    suspend fun getIssue(): Issue {
        return IssueRepository.getInstance().getIssue(this)
    }
}
