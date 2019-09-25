package de.taz.app.android.api.models

import de.taz.app.android.api.dto.SectionDto
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.interfaces.Downloadable

data class Section(
    val sectionHtml: FileEntry,
    val title: String,
    val type: SectionType,
    val articleList: List<Article> = emptyList(),
    val imageList: List<FileEntry> = emptyList()
): Downloadable {
    constructor(sectionDto: SectionDto) : this(
        sectionDto.sectionHtml,
        sectionDto.title,
        sectionDto.type,
        sectionDto.articleList?.map { Article(it) } ?: listOf(),
        sectionDto.imageList ?: listOf()
    )

    override fun getAllFileNames(): List<String> {
        val list = mutableListOf(sectionHtml.name)
        list.addAll(imageList.map { image -> image.name })
        articleList.forEach{ article -> list.addAll(article.getAllFileNames()) }
        return list
    }

}

