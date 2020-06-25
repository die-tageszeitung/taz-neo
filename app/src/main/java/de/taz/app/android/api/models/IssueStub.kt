package de.taz.app.android.api.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

@Entity(
    tableName = "Issue",
    primaryKeys = ["feedName", "date", "status"]
)
data class IssueStub(
    override val feedName: String,
    override val date: String,
    val key: String? = null,
    override val baseUrl: String,
    override val status: IssueStatus,
    override val minResourceVersion: Int,
    @ColumnInfo(defaultValue = "0") override val isWeekend: Boolean,
    override val dateDownload: Date?,
    val downloadedStatus: DownloadStatus?
): IssueOperations {

    constructor(issue: Issue): this (
        issue.feedName, issue.date, issue.key, issue.baseUrl, issue.status,
        issue.minResourceVersion, issue.isWeekend, issue.dateDownload, issue.downloadedStatus
    )

    suspend fun getIssue(): Issue {
        return withContext(Dispatchers.IO) {
            IssueRepository.getInstance().getIssue(this@IssueStub)
        }
    }
}
