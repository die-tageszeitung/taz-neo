package de.taz.app.android.api.interfaces

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface SectionOperations : WebViewDisplayable {

    override val key: String
    val extendedTitle: String?
    val title: String

    val issueStub: IssueStub?
        get() = IssueRepository.getInstance().getIssueStubForSection(this.key)

    fun nextSectionStub(): SectionStub? {
        return SectionRepository.getInstance().getNextSectionStub(this.key)
    }

    fun previousSectionStub(): SectionStub? {
        return SectionRepository.getInstance().getPreviousSectionStub(this.key)
    }

    override fun getFile(): File? {
        return FileHelper.getInstance().getFile(this.key)
    }

    override fun getFilePath(): String? {
        return FileHelper.getInstance().getAbsoluteFilePath(this.key)
    }

    override fun previous(): SectionStub? {
        return previousSectionStub()
    }

    override fun next(): SectionStub? {
        return nextSectionStub()
    }

    fun getHeaderTitle(): String {
        return extendedTitle ?: title
    }

    override fun getIssueOperations(applicationContext: Context?) = issueStub

    suspend fun getNavButton(): Image = withContext(Dispatchers.IO) {
        return@withContext SectionRepository.getInstance().getNavButton(this@SectionOperations.key)
    }

    override fun isDownloadedLiveData(applicationContext: Context?): LiveData<Boolean> {
        return SectionRepository.getInstance(applicationContext).isDownloadedLiveData(this)
    }

    override fun getDownloadedStatus(applicationContext: Context?): DownloadStatus? {
        return SectionRepository.getInstance(applicationContext).get(key)?.downloadedStatus
    }

    override fun setDownloadStatus(downloadStatus: DownloadStatus) {
        SectionRepository.getInstance().apply {
            getStub(key)?.let { update(it.copy(downloadedStatus = downloadStatus)) }
        }
    }

}