package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.api.models.SectionBase

@Entity(
    tableName = "SectionArticleJoin",
    foreignKeys = [
        ForeignKey(
            entity = ArticleBase::class,
            parentColumns = ["articleFileName"],
            childColumns = ["articleFileName"]
        ),
        ForeignKey(
            entity = SectionBase::class,
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
