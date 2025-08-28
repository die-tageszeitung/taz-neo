package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

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
    val dateDownload: Date?,
    val baseUrl: String,
    val podcastFileName: String?,
) {
    constructor(page: Page) : this(
        page.pagePdf.name,
        page.title,
        page.pagina,
        page.type,
        page.frameList,
        page.dateDownload,
        page.baseUrl,
        page.podcast?.file?.name,
    )
}