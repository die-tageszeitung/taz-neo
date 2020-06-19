package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.IssueStatus
import java.util.*

interface IssueOperations {

    val baseUrl: String
    val feedName: String
    val date: String
    val status: IssueStatus
    val dateDownload: Date?
    val minResourceVersion: Int
    val isWeekend: Boolean

    val tag: String
        get() = "$feedName/$date/$status"

}
