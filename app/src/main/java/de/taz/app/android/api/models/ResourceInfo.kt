package de.taz.app.android.api.models

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.*

const val RESOURCE_FOLDER = "resources"
const val RESOURCE_TAG = "resources"

data class ResourceInfo(
    val resourceVersion: Int,
    val resourceBaseUrl: String,
    val resourceZip: String,
    val resourceList: List<FileEntry>,
    override val downloadedStatus: DownloadStatus?
) : CacheableDownload {
    constructor(productDto: ProductDto) : this(
        productDto.resourceVersion!!,
        productDto.resourceBaseUrl!!,
        productDto.resourceZip!!,
        productDto.resourceList!!.map { FileEntry(it, RESOURCE_FOLDER) },
        DownloadStatus.pending
    )

    override suspend fun getAllFiles(): List<FileEntry> {
        return resourceList
    }

    override fun getAllFileNames(): List<String> {
        return resourceList.map { it.name }
    }

    override fun getAllLocalFileNames(): List<String> {
        return resourceList
            .filter { it.downloadedStatus == DownloadStatus.done }
            .map { it.name }
    }

    override fun getDownloadTag(): String? {
        return RESOURCE_TAG
    }

    override fun getDownloadedStatus(applicationContext: Context?): DownloadStatus? {
        return ResourceInfoRepository.getInstance(applicationContext).getNewest()?.downloadedStatus
    }

    override fun getLiveData(applicationContext: Context?): LiveData<ResourceInfo?> {
        return ResourceInfoRepository.getInstance(applicationContext).getLiveData()
    }

    override fun isDownloadedLiveData(applicationContext: Context?): LiveData<Boolean> {
        return ResourceInfoRepository.getInstance(applicationContext)
            .isDownloadedLiveData(this.resourceVersion)
    }

    override fun setDownloadStatus(downloadStatus: DownloadStatus) {
        ResourceInfoRepository.getInstance().apply {
            getStub()?.copy(downloadedStatus = downloadStatus)?.let { update(it) }
        }
    }

    companion object {

        private val log by Log

        private var lastUpdated = 0L
        private const val updateTimeOut = 3600000L // 1 hour

        fun getNewestDownloadedStubLiveData(applicationContext: Context?): LiveData<ResourceInfoStub?> {
            return ResourceInfoRepository.getInstance(applicationContext)
                .getNewestDownloadedLiveData()
        }

        suspend fun update(applicationContext: Context? = null, force: Boolean = false) {
            if(force || lastUpdated < DateHelper.now - updateTimeOut) {
                lastUpdated = DateHelper.now
                withContext(Dispatchers.IO) {
                    log.info("ResourceInfo.update called")
                    val apiService = ApiService.getInstance(applicationContext)
                    val resourceInfoRepository =
                        ResourceInfoRepository.getInstance(applicationContext)

                    val fromServer = try {
                        apiService.getResourceInfo()
                    } catch (e: ApiService.ApiServiceException) {
                        ToastHelper.getInstance().showNoConnectionToast()
                        null
                    }
                    val newest = resourceInfoRepository.getNewest()

                    fromServer?.let {
                        if (newest == null || fromServer.resourceVersion > newest.resourceVersion || !newest.isDownloaded(
                                applicationContext
                            )
                        ) {
                            resourceInfoRepository.save(fromServer)

                            // ensure resources are downloaded
                            DownloadService.getInstance(applicationContext).download(fromServer)
                            newest?.let { log.debug("Initialized ResourceInfo") }
                                ?: log.debug("Updated ResourceInfo")
                        }
                        resourceInfoRepository.deleteAllButNewestAndNewestDownloaded()
                    }
                }
            }
        }
    }
}