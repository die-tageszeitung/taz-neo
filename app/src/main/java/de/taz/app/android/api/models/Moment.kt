package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.MomentKey
import de.taz.app.android.persistence.repository.MomentRepository
import java.util.*

data class Moment(
    val issueFeedName: String,
    val issueDate: String,
    val issueStatus: IssueStatus,
    val baseUrl: String,
    val imageList: List<Image> = emptyList(),
    val creditList: List<Image> = emptyList(),
    val momentList: List<FileEntry> = emptyList(),
    override val dateDownload: Date?
) : DownloadableCollection {

    val issueKey = IssueKey(issueFeedName, issueDate, issueStatus)
    val momentKey = MomentKey(issueFeedName, issueDate, issueStatus)

    private fun getImagesToDownload(): List<Image> {
        return imageList.filter { it.resolution == ImageResolution.high }.distinct()
    }

    override suspend fun getAllFiles(): List<FileEntry> {
        val animatedList  = getFilesForAnimatedDownload().toMutableList()
        val bitmapList = getImagesToDownload().map { img -> FileEntry(img)}
        return animatedList + bitmapList
    }

    override suspend fun getAllFileNames(): List<String> {
        return getAllFiles().map { it.name }
    }

    private fun getFilesForAnimatedDownload(): List<FileEntry> {
        return momentList
    }

    override fun getDownloadTag(): String {
        return "moment/$issueFeedName/$issueDate"
    }

    override suspend fun getDownloadDate(applicationContext: Context): Date? {
        return MomentRepository.getInstance(applicationContext).getDownloadDate(this@Moment)
    }

    override suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        MomentRepository.getInstance(applicationContext).setDownloadDate(this@Moment, date)
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
        return momentList.firstOrNull {
            it.name.lowercase().endsWith("index.html")
        }
    }
}