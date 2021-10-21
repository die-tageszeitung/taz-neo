package de.taz.app.android.api.models

import android.content.Context
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
    override val key: String? = null,
    override val baseUrl: String,
    override val status: IssueStatus,
    override val minResourceVersion: Int,
    @ColumnInfo(defaultValue = "0") override val isWeekend: Boolean,
    override val moTime: String,
    override val dateDownload: Date?,
    override val dateDownloadWithPages: Date?,
    override val lastDisplayableName: String?,
    override val lastPagePosition: Int?
): IssueOperations {

    constructor(issue: IssueOperations): this (
        issue.feedName,
        issue.date,
        issue.key,
        issue.baseUrl,
        issue.status,
        issue.minResourceVersion,
        issue.isWeekend,
        issue.moTime,
        issue.dateDownload,
        issue.dateDownloadWithPages,
        issue.lastDisplayableName,
        issue.lastPagePosition
    )

    suspend fun getIssue(applicationContext: Context): Issue {
        return withContext(Dispatchers.IO) {
            IssueRepository.getInstance(applicationContext).getIssue(this@IssueStub)
        }
    }
}
