package de.taz.app.android.api.models

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
    val resourceList: List<FileEntry>
) : CacheableDownload {
    constructor(productDto: ProductDto) : this(
        productDto.resourceVersion!!,
        productDto.resourceBaseUrl!!,
        productDto.resourceZip!!,
        productDto.resourceList!!.map { FileEntry(it, RESOURCE_FOLDER) }
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

    companion object {

        private val log by Log

        suspend fun update() = withContext(Dispatchers.IO) {
            log.info("ResourceInfo.update called")
            val apiService = ApiService.getInstance()
            val fileEntryRepository = FileEntryRepository.getInstance()
            val resourceInfoRepository = ResourceInfoRepository.getInstance()

            try {
                val fromServer = apiService.getResourceInfo()
                val local = resourceInfoRepository.get()

                fromServer?.let {
                    if (local == null || fromServer.resourceVersion > local.resourceVersion || !local.isDownloadedOrDownloading()) {
                        val fromServerResourceListNames = fromServer.resourceList.map { it.name }
                        // delete unused files
                        local?.resourceList?.filter { local ->
                            local.name !in fromServerResourceListNames
                        }?.forEach {
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

                        resourceInfoRepository.save(fromServer)
                        resourceInfoRepository.deleteAllButNewest()

                        // ensure resources are downloaded
                        DownloadService.getInstance().apply {
                            download(fromServer)
                        }
                        local?.let { log.debug("Initialized ResourceInfo") }
                            ?: log.debug("Updated ResourceInfo")
                    }
                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                log.warn("Initializing ResourceInfo failed")
            }
        }

    }
}