package de.taz.app.android.api.interfaces

import android.content.Context
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import java.util.*

interface IssueOperations: DownloadableStub {

    val baseUrl: String
    val feedName: String
    val date: String
    val validityDate: String?
    val status: IssueStatus
    override val dateDownload: Date?
    val dateDownloadWithPages: Date?
    val minResourceVersion: Int
    val isWeekend: Boolean
    val moTime: String
    val lastDisplayableName: String?
    val lastPagePosition: Int?
    val key: String?
    val lastViewedDate: Date?

    val tag: String
        get() = "$feedName/$date/$status"

    val issueKey: AbstractIssueKey

    override fun getDownloadTag(): String {
        return tag
    }

    override suspend fun getDownloadDate(applicationContext: Context): Date? {
        return IssueRepository.getInstance(applicationContext).getDownloadDate(this@IssueOperations)
    }

    override suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        return IssueRepository.getInstance(applicationContext).setDownloadDate(this@IssueOperations, date)
    }
}
