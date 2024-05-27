package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.ArticleOperations
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
    override val articleType: ArticleType,
    val position: Int,
    val percentage: Int,
    override val dateDownload: Date?,
    val mediaSyncId: Int?,
    val chars: Int?,
    val words: Int?,
    val readMinutes: Int?,
    val pdfFileName: String?,
) : ArticleOperations {

    val hasAudio: Boolean
        get() = audioFileName != null

    constructor(article: Article) : this(
        article.articleHtml.name,
        article.issueFeedName,
        article.issueDate,
        article.title,
        article.teaser,
        article.onlineLink,
        article.pageNameList,
        article.bookmarkedTime,
        article.audio?.file?.name,
        article.articleType,
        article.position,
        article.percentage,
        article.dateDownload,
        article.mediaSyncId,
        article.chars,
        article.words,
        article.readMinutes,
        article.pdf?.name,
    )

    @Ignore
    override val key: String = articleFileName

}

/**
 * Used to update bookmark states with partial updates.
 * See https://developer.android.com/reference/androidx/room/Update
 */
data class ArticleBookmarkTime(
    val articleFileName: String,
    val bookmarkedTime: Date?,
)