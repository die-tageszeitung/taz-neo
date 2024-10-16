package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.persistence.repository.SectionRepository
import java.util.*

data class Section(
    val sectionHtml: FileEntry,
    val issueDate: String,
    override val title: String,
    val type: SectionType,
    val navButton: Image,
    val articleList: List<Article>,
    val imageList: List<Image>,
    override val extendedTitle: String?,
    override val dateDownload: Date?,
    val podcast: Audio?,
) : SectionOperations, WebViewDisplayable {

    override val key: String
        get() = sectionHtml.name

    override suspend fun getAllFiles(applicationContext: Context): List<FileEntry> {
        val list = mutableListOf(sectionHtml)
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal }.map { FileEntry(it) })
        return list.distinct()
    }

    override fun getDownloadTag(): String {
        return sectionHtml.name
    }

    override suspend fun previous(applicationContext: Context): Section? {
        val sectionRepository = SectionRepository.getInstance(applicationContext)
        return sectionRepository.getPreviousSectionStub(this.key)?.let {
            sectionRepository.sectionStubToSection(it)
        }
    }

    override suspend fun next(applicationContext: Context): Section? {
        val sectionRepository = SectionRepository.getInstance(applicationContext)
        return sectionRepository.getNextSectionStub(this.key)?.let {
            sectionRepository.sectionStubToSection(it)
        }
    }

    override suspend fun getIssueStub(applicationContext: Context): IssueStub? {
        return super.getIssueStub(applicationContext)
    }

    override suspend fun getDownloadDate(applicationContext: Context): Date? {
        return SectionRepository.getInstance(applicationContext).getDownloadDate(SectionStub(this))
    }

    override suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        SectionRepository.getInstance(applicationContext).setDownloadDate(SectionStub(this), date)
    }
}

