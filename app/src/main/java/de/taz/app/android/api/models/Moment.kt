package de.taz.app.android.api.models

import de.taz.app.android.api.dto.MomentDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.persistence.repository.IssueRepository

data class Moment(
    val imageList: List<FileEntry> = emptyList(),
    val creditList: List<Image> = emptyList()
): CacheableDownload {
    constructor(issueFeedName: String, issueDate: String, momentDto: MomentDto): this(
        momentDto.imageList
            ?.map { FileEntry(it, "$issueFeedName/$issueDate") } ?: emptyList(),
        momentDto.creditList
            ?.map { Image(it, "$issueFeedName/$issueDate") } ?: emptyList()
    )

    private fun getImagesToDownload(): List<FileEntry> {
        // TODO quickfix should be filtered by ImageResolution
        return imageList.filter { it.name.contains(".normal.") || it.name.contains(".quadrat")}.distinct()
    }

    override suspend fun getAllFiles(): List<FileEntry> {
        return getImagesToDownload()
    }

    override fun getAllFileNames(): List<String> {
        return getImagesToDownload().map { it.name }
    }

    fun getMomentFileToShare(): FileEntryOperations {
        return if (creditList.isNotEmpty()) {
            creditList.first { it.resolution == ImageResolution.high }
        } else {
            imageList.first { it.name.contains(".high.") }
        }
    }

    fun getIssueStub(): IssueStub {
        return IssueRepository.getInstance().getIssueStubForMoment(this)
    }

    override fun getIssueOperations(): IssueOperations? {
        return getIssueStub()
    }

    fun getMomentImage(): FileEntry {
        // TODO quickfix filter by ImageResolution (and use high if device resolution requires it)
        return imageList.first { it.name.contains(".norm") || it.name.contains(".quadrat") }
    }
}