package de.taz.app.android.api.interfaces

import android.content.Context
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import java.util.*

interface IssueOperations: DownloadableStub {

    val baseUrl: String
    val feedName: String
    val date: String
    val status: IssueStatus
    override val dateDownload: Date?
    val dateDownloadWithPages: Date?
    val minResourceVersion: Int
    val isWeekend: Boolean
    val moTime: String
    val lastDisplayableName: String?
    val lastPagePosition: Int?
    val key: String?

    val tag: String
        get() = "$feedName/$date/$status"

    val issueKey: IssueKey
        get() = IssueKey(feedName, date, status)

    override fun getDownloadTag(): String {
        return tag
    }

    override fun getDownloadDate(context: Context?): Date? {
        return IssueRepository.getInstance(context).getDownloadDate(this@IssueOperations)
    }

    override fun setDownloadDate(date: Date?, context: Context?) {
        return IssueRepository.getInstance(context).setDownloadDate(this@IssueOperations, date)
    }
}
