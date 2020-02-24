package de.taz.app.android.api.models

import de.taz.app.android.api.dto.SectionDto
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.singletons.FileHelper
import java.io.File

data class Section(
    val sectionHtml: FileEntry,
    val issueDate: String,
    val title: String,
    val type: SectionType,
    val articleList: List<Article> = emptyList(),
    val imageList: List<FileEntry> = emptyList(),
    val extendedTitle: String? = null
) : SectionOperations, CacheableDownload, WebViewDisplayable {
    constructor(issueFeedName: String, issueDate: String, sectionDto: SectionDto) : this(
        FileEntry(sectionDto.sectionHtml, "$issueFeedName/$issueDate"),
        issueDate,
        sectionDto.title,
        sectionDto.type,
        sectionDto.articleList?.map { Article(issueFeedName, issueDate, it) } ?: listOf(),
        sectionDto.imageList?.map { FileEntry(it, "$issueFeedName/$issueDate") } ?: listOf(),
        sectionDto.extendedTitle
    )

    override val sectionFileName: String
        get() = sectionHtml.name

    override fun getAllFiles(): List<FileEntry> {
        val list = mutableListOf(sectionHtml)
        list.addAll(imageList.filter { it.name.contains(".norm.") })
        return list.distinct()
    }

    override fun getFile(): File? {
        return FileHelper.getInstance().getFile(sectionHtml)
    }

    fun getHeaderTitle(): String {
        return extendedTitle ?: title
    }

    override fun previous(): Section? {
        return previousSection()
    }

    override fun next(): Section? {
        return nextSection()
    }

    override fun getIssueOperations() = issueStub
}

