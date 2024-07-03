package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ImageStub

@Entity(
    tableName = "ArticleImageJoin",
    foreignKeys = [
        ForeignKey(
            entity = ArticleStub::class,
            parentColumns = ["articleFileName"],
            childColumns = ["articleFileName"]
        ),
        ForeignKey(
            entity = ImageStub::class,
            parentColumns = ["fileEntryName"],
            childColumns = ["imageFileName"]
        )
    ],
    primaryKeys = ["articleFileName", "imageFileName"],
    indices = [Index("imageFileName")]
)
data class ArticleImageJoin(
    val articleFileName: String,
    val imageFileName: String,
    val index: Int
)