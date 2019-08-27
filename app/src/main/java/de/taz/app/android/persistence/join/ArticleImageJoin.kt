package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import de.taz.app.android.api.models.*

@Entity(tableName = "ArticleImage",
    foreignKeys = [
        ForeignKey(entity = ArticleBase::class,
            parentColumns = ["articleFileName"],
            childColumns = ["articleFileName"]),
        ForeignKey(entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["imageFileName"])
    ],
    primaryKeys = ["articleFileName", "imageFileName"]
)
class ArticleImageJoin(
    val articleFileName: String,
    val imageFileName: String
)