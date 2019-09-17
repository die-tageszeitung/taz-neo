package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ArticleDto
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
) {
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

    fun isDownloaded(): Boolean {
        DownloadRepository.getInstance().let {
            return it.isDownloaded(articleHtml.name) &&
                    it.isDownloaded(audioFile?.name) &&
                    it.isDownloaded(imageList.map { image -> image.name }) &&
                    authorList.firstOrNull { author -> !author.isDownloaded() } == null
        }
    }

}
