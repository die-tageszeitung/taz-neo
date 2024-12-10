package de.taz.app.android.api.interfaces

import android.content.Context
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionType
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import java.util.Date

interface SectionOperations: WebViewDisplayable {

    val issueDate: String
    val extendedTitle: String?
    val title: String
    val type: SectionType

    fun getHeaderTitle(): String {
        return extendedTitle ?: title
    }

    override suspend fun getIssueStub(applicationContext: Context): IssueStub? {
        return IssueRepository.getInstance(applicationContext).getIssueStubForSection(key)
    }

    override suspend fun previous(applicationContext: Context): SectionOperations? {
        val sectionRepository = SectionRepository.getInstance(applicationContext)
        return sectionRepository.getPreviousSectionStub(this.key)
    }

    override suspend fun next(applicationContext: Context): SectionOperations? {
        val sectionRepository = SectionRepository.getInstance(applicationContext)
        return sectionRepository.getNextSectionStub(this.key)
    }

    override suspend fun getDownloadDate(applicationContext: Context): Date? {
        return SectionRepository.getInstance(applicationContext).getDownloadDate(this)
    }

    override suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        SectionRepository.getInstance(applicationContext).setDownloadDate(this, date)
    }

    suspend fun getPodcast(applicationContext: Context): Audio?

    suspend fun getPodcastImage(applicationContext: Context): Image?
}