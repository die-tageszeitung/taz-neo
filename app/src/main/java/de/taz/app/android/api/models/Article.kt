package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ArticleDto

class Article(articleDto: ArticleDto) {
    val articleHtml: FileEntry = articleDto.articleHtml
    val title: String? = null
    val teaser: String? = null
    val onlineLink: String? = null
    val audioFile: FileEntry? = null
    val pageNameList: List<String>? = null
    val imageList: List<Image>? = null
    val authorList: List<Author>? = null
}
