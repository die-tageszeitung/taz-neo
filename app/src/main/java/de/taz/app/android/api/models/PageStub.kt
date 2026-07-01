package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Page",
    foreignKeys = [
        ForeignKey(
            entity = AudioStub::class,
            parentColumns = ["fileName"],
            childColumns = ["podcastFileName"]
        )
    ],
    indices = [
        Index("podcastFileName")
    ]
)
data class PageStub(
    @PrimaryKey val pdfFileName: String,
    val title: String? = null,
    val pagina: String? = null,
    val type: PageType? = null,
    val frameList: List<Frame>? = null,
    val baseUrl: String,
    val podcastFileName: String?,
    val adIdList: List<String>?,
)