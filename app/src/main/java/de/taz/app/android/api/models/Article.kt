package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ArticleDto
import de.taz.app.android.api.interfaces.Downloadable
import de.taz.app.android.persistence.repository.DownloadRepository

data class Article(
    val articleHtml: FileEntry,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val audioFile: FileEntry?,
    val pageNameList: List<String> = emptyList(),
    val imageList: List<FileEntry> = emptyList(),
    val authorList: List<Author> = emptyList()
): Downloadable {
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

    override fun getAllFileNames(): List<String> {
        val list = mutableListOf(articleHtml.name)
        audioFile?.let { list.add(audioFile.name) }
        list.addAll(imageList.map { image -> image.name })
        return list
    }

}
