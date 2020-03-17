package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.repository.FeedRepository
import java.util.*

interface IssueOperations {

    val baseUrl: String
    val feedName: String
    val date: String
    val status: IssueStatus
    val dateDownload: Date?

    val tag: String
        get() = "$feedName/$date"

    fun getFeed(): Feed {
        return FeedRepository.getInstance().get(feedName)
    }

}
