package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.FileEntry

@Entity(
    tableName = "ArticleAudioFileJoin",
    foreignKeys = [
        ForeignKey(
            entity = ArticleStub::class,
            parentColumns = ["articleFileName"],
            childColumns = ["articleFileName"]
        ),
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["audioFileName"]
        )
    ],
    primaryKeys = ["articleFileName", "audioFileName"],
    indices = [Index("articleFileName"), Index("audioFileName")]
)
data class ArticleAudioFileJoin(
    val articleFileName: String,
    val audioFileName: String
)