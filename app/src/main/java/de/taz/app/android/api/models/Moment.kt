package de.taz.app.android.api.models

import de.taz.app.android.api.dto.MomentDto
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.singletons.FileHelper
import java.util.*

data class Moment(
    val issueFeedName: String,
    val issueDate: String,
    val issueStatus: IssueStatus,
    val imageList: List<Image> = emptyList(),
    val creditList: List<Image> = emptyList(),
    val momentList: List<FileEntry> = emptyList(),
    override val dateDownload: Date?
) : DownloadableCollection {

    constructor(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus,
        momentDto: MomentDto
    ) : this(
        issueFeedName,
        issueDate,
        issueStatus,
        momentDto.imageList
            ?.map { Image(it, "$issueFeedName/$issueDate") } ?: emptyList(),
        momentDto.creditList
            ?.map { Image(it, "$issueFeedName/$issueDate") } ?: emptyList(),
        momentDto.momentList
            ?.map { FileEntry(it, "$issueFeedName/$issueDate") } ?: emptyList(),
        null
    )

    constructor(issueOperations: IssueOperations, momentDto: MomentDto) : this(
        issueOperations.feedName,
        issueOperations.date,
        issueOperations.status,
        momentDto.imageList
            ?.map { Image(it, "${issueOperations.feedName}/${issueOperations.date}") }
            ?: emptyList(),
        momentDto.creditList
            ?.map { Image(it, "${issueOperations.feedName}/${issueOperations.date}") }
            ?: emptyList(),
        momentDto.momentList
            ?.map { FileEntry(it, "${issueOperations.feedName}/${issueOperations.date}") }
            ?: emptyList(),
        null
    )

    private fun getImagesToDownload(): List<Image> {
        return imageList.filter { it.resolution == ImageResolution.high }.distinct()
    }

    override fun getAllFiles(): List<FileEntry> {
        val animatedList  = getFilesForAnimatedDownload().toMutableList()
        val bitmapList = getImagesToDownload().map { img -> FileEntry(img)}
        return animatedList + bitmapList
    }

    override fun getAllFileNames(): List<String> {
        return getAllFiles().map { it.name }
    }

    override suspend fun deleteFiles() {
        getAllFiles().forEach { FileHelper.getInstance().deleteFile(it) }
    }

    private fun getFilesForAnimatedDownload(): List<FileEntry> {
        return momentList
    }

    override fun getDownloadTag(): String {
        return "moment/$issueFeedName/$issueDate"
    }

    override fun getDownloadDate(): Date? {
        return MomentRepository.getInstance().getDownloadDate(this@Moment)
    }

    override fun setDownloadDate(date: Date?) {
        MomentRepository.getInstance().setDownloadDate(this@Moment, date)
    }

    fun getMomentFileToShare(): FileEntryOperations {
        return if (creditList.isNotEmpty()) {
            creditList.first { it.resolution == ImageResolution.high }
        } else {
            imageList.first { it.resolution == ImageResolution.high }
        }
    }

    fun getMomentImage(): Image? {
        return imageList.firstOrNull { it.resolution == ImageResolution.high }
            ?: imageList.firstOrNull { it.resolution == ImageResolution.normal }
            ?: imageList.firstOrNull { it.resolution == ImageResolution.small }
    }

    fun getIndexHtmlForAnimated(): FileEntry? {
        return momentList.firstOrNull { it.name.endsWith(".html") }
    }
}