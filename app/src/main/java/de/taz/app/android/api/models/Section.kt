package de.taz.app.android.api.models

import de.taz.app.android.api.dto.SectionDto
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.util.FileHelper
import java.io.File

data class Section(
    val sectionHtml: FileEntry,
    val title: String,
    val type: SectionType,
    val articleList: List<Article> = emptyList(),
    val imageList: List<FileEntry> = emptyList()
) : SectionOperations, CacheableDownload, WebViewDisplayable {
    constructor(sectionDto: SectionDto) : this(
        sectionDto.sectionHtml,
        sectionDto.title,
        sectionDto.type,
        sectionDto.articleList?.map { Article(it) } ?: listOf(),
        sectionDto.imageList ?: listOf()
    )

    override val sectionFileName: String
        get() = sectionHtml.name

    override fun getAllFiles(): List<FileEntry> {
        val list = mutableListOf(sectionHtml)
        list.addAll(imageList)
        articleList.forEach { article -> list.addAll(article.getAllFiles()) }
        return list
    }

    override fun getFile(): File {
        return FileHelper.getInstance().getFile("${issueBase.tag}/$sectionFileName")
    }

    override fun previous(): Section? {
        return previousSection()
    }

    override fun next(): Section? {
        return nextSection()
    }

}

