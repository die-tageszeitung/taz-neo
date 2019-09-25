package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ArticleDto
import de.taz.app.android.api.interfaces.ArticleFunctions
import de.taz.app.android.api.interfaces.Downloadable
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.SectionRepository

data class Article(
    val articleHtml: FileEntry,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val audioFile: FileEntry?,
    val pageNameList: List<String> = emptyList(),
    val imageList: List<FileEntry> = emptyList(),
    val authorList: List<Author> = emptyList()
): ArticleFunctions, Downloadable {
    constructor(articleDto: ArticleDto) : this(
        articleDto.articleHtml,
        articleDto.title,
        articleDto.teaser,
        articleDto.onlineLink,
        articleDto.audioFile,
        articleDto.pageNameList ?: emptyList(),
        articleDto.imageList ?: emptyList(),
        articleDto.authorList ?: emptyList()
    )

    override val articleFileName
        get() = articleHtml.name

    override fun getAllFileNames(): List<String> {
        val list = mutableListOf(articleFileName)
        audioFile?.let { list.add(audioFile.name) }
        list.addAll(imageList.map { image -> image.name })
        return list
    }


}
