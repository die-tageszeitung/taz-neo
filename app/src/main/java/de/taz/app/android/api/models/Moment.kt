package de.taz.app.android.api.models

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.api.dto.MomentDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import io.sentry.Sentry

data class Moment(
    val issueFeedName: String,
    val issueDate: String,
    val issueStatus: IssueStatus,
    val imageList: List<Image> = emptyList(),
    val creditList: List<Image> = emptyList(),
    override val downloadedStatus: DownloadStatus?
) : CacheableDownload {

    constructor(issueFeedName: String, issueDate: String, issueStatus: IssueStatus, momentDto: MomentDto) : this(
        issueFeedName,
        issueDate,
        issueStatus,
        momentDto.imageList
            ?.map { Image(it, "$issueFeedName/$issueDate") } ?: emptyList(),
        momentDto.creditList
            ?.map { Image(it, "$issueFeedName/$issueDate") } ?: emptyList(),
        DownloadStatus.pending
    )

    constructor(issueOperations: IssueOperations, momentDto: MomentDto) : this(
        issueOperations.feedName,
        issueOperations.date,
        issueOperations.status,
        momentDto.imageList
            ?.map { Image(it, "${issueOperations.feedName}/${issueOperations.date}") } ?: emptyList(),
        momentDto.creditList
            ?.map { Image(it, "${issueOperations.feedName}/${issueOperations.date}") } ?: emptyList(),
        DownloadStatus.pending
    )

    private fun getImagesToDownload(): List<Image> {
        return imageList.filter { it.resolution == ImageResolution.high }.distinct()
    }

    override suspend fun getAllFiles(): List<Image> {
        return getImagesToDownload()
    }

    override fun getAllFileNames(): List<String> {
        return getImagesToDownload().map { it.name }
    }

    override fun getAllLocalFileNames(): List<String> {
        return getImagesToDownload()
            .filter { it.downloadedStatus == DownloadStatus.done }
            .map { it.name }
    }

    fun getMomentFileToShare(): FileEntryOperations {
        return if (creditList.isNotEmpty()) {
            creditList.first { it.resolution == ImageResolution.high }
        } else {
            imageList.first { it.resolution == ImageResolution.high }
        }
    }

    private fun getIssueStub(applicationContext: Context?): IssueStub? {
        return IssueRepository.getInstance(applicationContext).getIssueStubForMoment(this)
    }

    override fun getIssueOperations(applicationContext: Context?): IssueOperations? {
        return getIssueStub(applicationContext)
    }

    fun getMomentImage(): Image? {
        return imageList.firstOrNull { it.resolution == ImageResolution.high }
            ?: imageList.firstOrNull { it.resolution == ImageResolution.normal }
            ?: imageList.firstOrNull { it.resolution == ImageResolution.small }
    }

    override fun setDownloadStatus(downloadStatus: DownloadStatus) {
        MomentRepository.getInstance().apply  {
            getStub(this@Moment.issueFeedName, this@Moment.issueDate, this@Moment.issueStatus)?.let {
                update(it.copy(downloadedStatus = downloadStatus))
            }
        }
    }

    override fun isDownloadedLiveData(applicationContext: Context?): LiveData<Boolean> {
        return Transformations.distinctUntilChanged(
            DownloadRepository.getInstance(applicationContext)
                .isDownloadedLiveData(getAllFileNames())
        )
    }

    override fun getDownloadedStatus(applicationContext: Context?): DownloadStatus? {
        return MomentRepository.getInstance(applicationContext).getDownloadedStatus(
            this.issueFeedName, this.issueDate, this.issueStatus
        )
    }

    override fun getLiveData(applicationContext: Context?): LiveData<Moment?> {
        return this@Moment.getIssueOperations(applicationContext)?.let {
            MomentRepository.getInstance(applicationContext).getLiveData(it)
        } ?: run {
            Sentry.capture("Could not get IssueOperations for Moment: $this")
            MutableLiveData<Moment?>(null)
        }
    }

}