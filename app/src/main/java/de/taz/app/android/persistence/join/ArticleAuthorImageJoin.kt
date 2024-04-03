package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.FileEntry

@Entity(
    tableName = "ArticleAuthor",
    foreignKeys = [
        ForeignKey(
            entity = ArticleStub::class,
            parentColumns = ["articleFileName"],
            childColumns = ["articleFileName"]
        ),
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["authorFileName"]
        )
    ],
    indices = [Index("authorFileName"), Index("articleFileName")]
)
data class ArticleAuthorImageJoin(
    val articleFileName: String,
    val authorName: String?,
    val authorFileName: String?,
    val index: Int,
    @PrimaryKey(autoGenerate = true) val id: Int? = null
)