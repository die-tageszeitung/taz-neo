package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ArticleDto

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Article

        return articleHtml == other.articleHtml &&
                title == other.title &&
                teaser == other.teaser &&
                onlineLink == other.onlineLink &&
                audioFile == other.audioFile &&
                pageNameList.containsAll(other.pageNameList) &&
                imageList.containsAll(other.imageList) &&
                authorList.containsAll(other.authorList)
    }

}
