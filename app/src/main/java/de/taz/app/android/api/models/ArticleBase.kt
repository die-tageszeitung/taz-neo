package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.ArticleDto

@Entity(tableName = "Article")
class ArticleBase(
    @PrimaryKey private val articleId: String,
    //val articleHtml: FileEntry,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    //val audioFile: FileEntry?,
    val pageNameList: List<String>?
    //val imageList: List<Image>?,
    //val authorList: List<Author>?
) {
    constructor(articleDto: ArticleDto) : this(
        articleDto.articleHtml.name,
        articleDto.title,
        articleDto.teaser,
        articleDto.onlineLink,
        articleDto.pageNameList
    )
}
