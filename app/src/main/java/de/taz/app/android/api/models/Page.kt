package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.persistence.repository.PageRepository
import java.util.*


data class Page(
    val pagePdf: FileEntry,
    val title: String? = null,
    val pagina: String? = null,
    val type: PageType? = null,
    val frameList: List<Frame>? = null,
    override val dateDownload: Date?,
    val baseUrl: String,
    val podcast: Audio?,
) : DownloadableCollection {

    override suspend fun getDownloadDate(applicationContext: Context): Date? {
        return PageRepository.getInstance(applicationContext).getDownloadDate(this)
    }

    override suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        PageRepository.getInstance(applicationContext).setDownloadDate(this, date)
    }

    override suspend fun getAllFiles(applicationContext: Context): List<FileEntry> {
        return listOf(pagePdf)
    }

    override fun getDownloadTag(): String {
        return "page/${pagePdf.name}"
    }
}

enum class PageType {
    left,
    right,
    panorama
}
