package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Page")
data class PageStub(
    @PrimaryKey val pdfFileName: String,
    val title: String? = null,
    val pagina: String? = null,
    val type: PageType? = null,
    val frameList: List<Frame>? = null,
    val downloadedField: Boolean? = null
) {
    constructor(page: Page) : this(
        page.pagePdf.name,
        page.title,
        page.pagina,
        page.type,
        page.frameList,
        page.downloadedField
    )
}
