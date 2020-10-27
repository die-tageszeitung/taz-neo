package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

interface IssueOperations: DownloadableStub {

    val baseUrl: String
    val feedName: String
    val date: String
    val status: IssueStatus
    override val dateDownload: Date?
    val minResourceVersion: Int
    val isWeekend: Boolean
    val moTime: String

    val tag: String
        get() = "$feedName/$date/$status"

    val issueKey: IssueKey
        get() = IssueKey(feedName, date, status)

    override fun getDownloadTag(): String {
        return tag
    }

    override suspend fun getDownloadDate(): Date? = withContext(Dispatchers.IO) {
        IssueRepository.getInstance().getDownloadDate(this@IssueOperations)
    }

    override suspend fun setDownloadDate(date: Date?) = withContext(Dispatchers.IO) {
        IssueRepository.getInstance().setDownloadDate(this@IssueOperations, date)
    }
}
