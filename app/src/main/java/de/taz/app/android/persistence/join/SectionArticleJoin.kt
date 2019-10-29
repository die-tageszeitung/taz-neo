package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.SectionStub

@Entity(
    tableName = "SectionArticleJoin",
    foreignKeys = [
        ForeignKey(
            entity = ArticleStub::class,
            parentColumns = ["articleFileName"],
            childColumns = ["articleFileName"]
        ),
        ForeignKey(
            entity = SectionStub::class,
            parentColumns = ["sectionFileName"],
            childColumns = ["sectionFileName"]
        )
    ],
    primaryKeys = ["articleFileName", "sectionFileName"],
    indices = [Index("sectionFileName")]
)
data class SectionArticleJoin(
    val sectionFileName: String,
    val articleFileName: String,
    val index: Int
)
