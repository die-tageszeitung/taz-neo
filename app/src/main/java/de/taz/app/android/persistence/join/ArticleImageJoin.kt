package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.*

@Entity(
    tableName = "ArticleImageJoin",
    foreignKeys = [
        ForeignKey(
            entity = ArticleBase::class,
            parentColumns = ["articleFileName"],
            childColumns = ["articleFileName"]
        ),
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["imageFileName"]
        )
    ],
    primaryKeys = ["articleFileName", "imageFileName"],
    indices = [Index("imageFileName")]
)
data class ArticleImageJoin(
    val articleFileName: String,
    val imageFileName: String
)