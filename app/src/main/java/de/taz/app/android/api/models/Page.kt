package de.taz.app.android.api.models

import android.content.Context
import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.PageDto
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.singletons.StorageService
import java.util.*


data class Page(
    val pagePdf: FileEntry,
    val title: String? = null,
    val pagina: String? = null,
    val type: PageType? = null,
    val frameList: List<Frame>? = null,
    override val dateDownload: Date?
) : DownloadableCollection {

    constructor(issueKey: IssueKey, pageDto: PageDto) : this(
        FileEntry(pageDto.pagePdf, StorageService.determineFilePath(pageDto.pagePdf, issueKey)),
        pageDto.title,
        pageDto.pagina,
        pageDto.type,
        pageDto.frameList,
        null
    )

    override fun getDownloadDate(context: Context?): Date? {
        return PageRepository.getInstance(context).getDownloadDate(this)
    }

    override fun setDownloadDate(date: Date?, context: Context?) {
        PageRepository.getInstance(context).setDownloadDate(this, date)
    }

    override fun getAllFiles(): List<FileEntry> {
        return listOf(pagePdf)
    }

    override fun getAllFileNames(): List<String> {
        return getAllFiles().map { it.name }.distinct()
    }

    override fun getDownloadTag(): String {
        return "page/${pagePdf.sha256}"
    }
}

@JsonClass(generateAdapter = false)
enum class PageType {
    left,
    right,
    panorama
}
