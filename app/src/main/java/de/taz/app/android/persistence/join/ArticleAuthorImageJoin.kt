package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.api.models.FileEntry

@Entity(
    tableName = "ArticleAuthor",
    foreignKeys = [
        ForeignKey(
            entity = ArticleBase::class,
            parentColumns = ["articleFileName"],
            childColumns = ["articleFileName"]
        ),
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["authorFileName"]
        )
    ],
    primaryKeys = ["articleFileName", "authorName", "authorFileName"]
)
class ArticleAuthorImageJoin(
    val articleFileName: String,
    val authorName: String?,
    val authorFileName: String?
)