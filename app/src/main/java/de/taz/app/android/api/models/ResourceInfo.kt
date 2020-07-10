package de.taz.app.android.api.models

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    override fun getDownloadTag(): String? {
        return RESOURCE_TAG
    }

    override fun getLiveData(applicationContext: Context?): LiveData<ResourceInfo?> {
        return ResourceInfoRepository.getInstance(applicationContext).getLiveData()
    }

    override fun isDownloadedLiveData(applicationContext: Context?): LiveData<Boolean> {
        return ResourceInfoRepository.getInstance(applicationContext)
            .isDownloadedLiveData(this.resourceVersion)
    }

    override fun setDownloadStatus(downloadStatus: DownloadStatus) {
        ResourceInfoRepository.getInstance()
            .update(ResourceInfoStub(this).copy(downloadedStatus = downloadStatus))
    }

    companion object {

        private val log by Log

        suspend fun get(applicationContext: Context?): ResourceInfo? {
            return ResourceInfoRepository.getInstance(applicationContext).get() ?: update(
                applicationContext
            )
        }

        suspend fun update(applicationContext: Context?): ResourceInfo? = withContext(Dispatchers.IO) {
            log.info("ResourceInfo.update called")
            val apiService = ApiService.getInstance(applicationContext)
            val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
            val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)

            val fromServer = apiService.getResourceInfoAsync().await()
            val local = resourceInfoRepository.get()

            fromServer?.let {
                if (local == null || fromServer.resourceVersion > local.resourceVersion || !local.isDownloaded(
                        applicationContext
                    )
                ) {
                    if (local != null) {
                        val fromServerResourceListNames = fromServer.resourceList.map { it.name }
                        // delete unused files
                        local.resourceList.filter { local ->
                            local.name !in fromServerResourceListNames
                        }.forEach {
                            log.info("deleting ${it.name}")
                            it.deleteFile()
                        }

                        // delete modified files
                        fromServer.resourceList.forEach { newFileEntry ->
                            fileEntryRepository.get(newFileEntry.name)?.let { oldFileEntry ->
                                if (oldFileEntry != newFileEntry) {
                                    oldFileEntry.deleteFile()
                                }
                            }
                        }
                    }

                    resourceInfoRepository.save(fromServer)

                    // ensure resources are downloaded
                    DownloadService.getInstance(applicationContext).download(fromServer)
                    local?.let { log.debug("Initialized ResourceInfo") }
                        ?: log.debug("Updated ResourceInfo")
                }
                resourceInfoRepository.deleteAllButNewest()
                fromServer
            }
        }

    }
}