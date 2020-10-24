package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.PageDto
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.singletons.FileHelper
import java.util.*


data class Page(
    val pagePdf: FileEntry,
    val title: String? = null,
    val pagina: String? = null,
    val type: PageType? = null,
    val frameList: List<Frame>? = null,
    override val dateDownload: Date?
) : DownloadableCollection {

    constructor(issueFeedName: String, issueDate: String, pageDto: PageDto) : this(
        FileEntry(pageDto.pagePdf, "$issueFeedName/$issueDate"),
        pageDto.title,
        pageDto.pagina,
        pageDto.type,
        pageDto.frameList,
        null
    )

    override suspend fun getDownloadDate(): Date? {
        return PageRepository.getInstance().getDownloadDate(this)
    }

    override suspend fun setDownloadDate(date: Date?) {
        return PageRepository.getInstance().setDownloadDate(this, date)
    }

    override fun getAllFiles(): List<FileEntry> {
        return listOf(pagePdf)
    }

    override fun getAllFileNames(): List<String> {
        return getAllFiles().map { it.name }.distinct()
    }

    override suspend fun deleteFiles() {
        getAllFiles().forEach {
            FileEntryRepository.getInstance().resetDownloadDate(it)
            FileHelper.getInstance().deleteFile(it)
        }
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
