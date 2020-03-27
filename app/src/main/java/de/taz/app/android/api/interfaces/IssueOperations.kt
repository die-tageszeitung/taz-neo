package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

interface IssueOperations {

    val baseUrl: String
    val feedName: String
    val date: String
    val status: IssueStatus
    val dateDownload: Date?
    val minResourceVersion: Int

    val tag: String
        get() = "$feedName/$date"

    fun getFeed(): Feed {
        return FeedRepository.getInstance().get(feedName)
    }

    suspend fun getNavButton(): Image = withContext(Dispatchers.IO) {
        val firstSection =
            SectionRepository.getInstance().getSectionStubsForIssue(this@IssueOperations).first()
        return@withContext SectionRepository.getInstance()
            .getNavButton(firstSection.sectionFileName)
    }

}
