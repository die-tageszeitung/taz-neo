package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.PageDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.persistence.repository.PageRepository


data class Page (
    val pagePdf: FileEntry,
    val title: String? = null,
    val pagina: String? = null,
    val type: PageType? = null,
    val frameList: List<Frame>? = null,
    override val downloadedField: Boolean? = false
) : CacheableDownload {

    constructor(issueFeedName: String, issueDate: String, pageDto: PageDto): this (
        FileEntry(pageDto.pagePdf, "$issueFeedName/$issueDate"),
        pageDto.title,
        pageDto.pagina,
        pageDto.type,
        pageDto.frameList
    )

    override suspend fun getAllFiles(): List<FileEntry> {
        return listOf(pagePdf)
    }

    override fun getAllFileNames(): List<String> {
        return listOf(pagePdf).map { it.name }.distinct()
    }

    override fun setIsDownloaded(downloaded: Boolean) {
        PageRepository.getInstance().update(PageStub(this).copy(downloadedField = downloaded))
    }
}

@JsonClass(generateAdapter = false)
enum class PageType {
    left,
    right,
    panorama
}
