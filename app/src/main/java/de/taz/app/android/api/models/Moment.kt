package de.taz.app.android.api.models

import de.taz.app.android.api.dto.MomentDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.persistence.repository.IssueRepository

data class Moment(
    val imageList: List<FileEntry> = emptyList()
): CacheableDownload {
    constructor(issueFeedName: String, issueDate: String, momentDto: MomentDto): this(
        momentDto.imageList
            ?.map { FileEntry(it, "$issueFeedName/$issueDate") } ?: emptyList()
    )

    override suspend fun getAllFiles(): List<FileEntry> {
        return imageList.filter { it.name.contains(".normal.") }.distinct()
    }

    override fun getAllFileNames(): List<String> {
        return imageList.filter { it.name.contains(".normal.") }.map { it.name }.distinct()
    }


    fun getIssueStub(): IssueStub {
        return IssueRepository.getInstance().getIssueStubForMoment(this)
    }

    override fun getIssueOperations(): IssueOperations? {
        return getIssueStub()
    }

    fun getMomentImage(): FileEntry {
        return imageList.first { it.name.contains(".normal.") }
    }
}