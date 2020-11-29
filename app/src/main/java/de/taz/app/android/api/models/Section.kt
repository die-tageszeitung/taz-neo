package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.dto.SectionDto
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.FileHelper
import java.io.File
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
    override val dateDownload: Date?
) : SectionOperations, WebViewDisplayable {

    constructor(issueFeedName: String, issueDate: String, sectionDto: SectionDto) : this(
        sectionHtml = FileEntry(sectionDto.sectionHtml, "$issueFeedName/$issueDate"),
        issueDate = issueDate,
        title = sectionDto.title,
        type = sectionDto.type,
        navButton = Image(sectionDto.navButton, "$issueFeedName/$issueDate"),
        articleList = sectionDto.articleList?.map { Article(issueFeedName, issueDate, it) }
            ?: listOf(),
        imageList = sectionDto.imageList?.map { Image(it, "$issueFeedName/$issueDate") }
            ?: listOf(),
        extendedTitle = sectionDto.extendedTitle,
        dateDownload = null
    )

    override val key: String
        get() = sectionHtml.name

    override fun getAllFiles(): List<FileEntry> {
        val list = mutableListOf(sectionHtml)
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal }.map { FileEntry(it) })
        return list.distinct()
    }

    override fun getAllFileNames(): List<String> {
        return getAllFiles().map { it.name }
    }

    override suspend fun deleteFiles() {
        getAllFiles().forEach { FileHelper.getInstance().deleteFile(it) }
    }

    override fun getDownloadTag(): String {
        return sectionHtml.name
    }

    fun nextSection(): Section? {
        val sectionRepository = SectionRepository.getInstance()
        return sectionRepository.getNextSectionStub(this.key)?.let {
            sectionRepository.sectionStubToSection(it)
        }
    }

    fun previousSection(): Section? {
        val sectionRepository = SectionRepository.getInstance()
        return sectionRepository.getPreviousSectionStub(this.key)?.let {
            sectionRepository.sectionStubToSection(it)
        }
    }

    override fun getFile(): File? {
        return FileHelper.getInstance().getFile(this.key)
    }

    override fun getFilePath(): String? {
        return FileHelper.getInstance().getAbsoluteFilePath(this.key)
    }

    override fun previous(): Section? {
        return previousSection()
    }

    override fun next(): Section? {
        return nextSection()
    }

    override fun getIssueStub(): IssueStub? {
        return super.getIssueStub()
    }

    override fun getDownloadDate(context: Context?): Date? {
        return SectionRepository.getInstance(context).getDownloadDate(SectionStub(this))
    }

    override fun setDownloadDate(date: Date?, context: Context?) {
        SectionRepository.getInstance(context).setDownloadDate(SectionStub(this), date)
    }
}

