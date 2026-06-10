package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.ArticleDto
import de.taz.app.android.api.dto.ImageResolutionDto
import de.taz.app.android.persistence.repository.IssueKey
import java.util.Date

@Entity(
    tableName = "Article",
    foreignKeys = [
        ForeignKey(
            entity = AudioStub::class,
            parentColumns = ["fileName"],
            childColumns = ["audioFileName"]
        ),
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["pdfFileName"]
        )
    ],
    indices = [
        Index("audioFileName"),
        Index("pdfFileName"),
    ]
)
data class ArticleStub(
    @PrimaryKey val articleFileName: String,
    val issueFeedName: String,
    val issueDate: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val pageNameList: List<String>,
    val bookmarkedTime: Date?,
    val audioFileName: String?,
    val articleType: ArticleType,
    val position: Int,
    val percentage: Int,
    val dateDownload: Date?,
    val mediaSyncId: Int?,
    val chars: Int?,
    val words: Int?,
    val readMinutes: Int?,
    val pdfFileName: String?,
    val iconFileName: String?,
) {

    constructor(issueKey: IssueKey, articleDto: ArticleDto, articleType: ArticleType?):this(
        articleDto.articleHtml.name,
        issueKey.feedName,
        issueKey.date,
        articleDto.title,
        articleDto.teaser,
        articleDto.onlineLink,
        articleDto.pageNameList ?: emptyList(),
        null,
        articleDto.audio?.file?.name,
        articleType ?: articleTypeMapper(articleDto.articleType),
        0,
        0,
        null,
        articleDto.mediaSyncId,
        articleDto.chars,
        articleDto.words,
        articleDto.readMinutes,
        articleDto.pdf?.name,
        articleDto.iconList?.firstOrNull { it.resolution == ImageResolutionDto.normal }?.name,
    )
}

/**
 * Used to update bookmark states with partial updates.
 * See https://developer.android.com/reference/androidx/room/Update
 */
data class ArticleBookmarkTime(
    val articleFileName: String,
    val bookmarkedTime: Date?,
)

    private fun articleTypeMapper(typeString: String?): ArticleType {
        return when (typeString) {
            "imprint" -> ArticleType.IMPRINT
            "podcast" -> ArticleType.PODCAST
            else -> ArticleType.STANDARD
        }
    }