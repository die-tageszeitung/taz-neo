package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ArticleDto

class Article(
    val articleHtml: FileEntry,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val audioFile: FileEntry?,
    val pageNameList: List<String>?,
    val imageList: List<Image>?,
    val authorList: List<Author>?
) {
    constructor(articleDto: ArticleDto) : this(
        articleDto.articleHtml,
        articleDto.title,
        articleDto.teaser,
        articleDto.onlineLink,
        articleDto.audioFile,
        articleDto.pageNameList,
        articleDto.imageList,
        articleDto.authorList
    )
}
