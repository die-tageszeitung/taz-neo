package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.singletons.FileHelper
import java.util.*

const val RESOURCE_FOLDER = "resources"
const val RESOURCE_TAG = "resources"

data class ResourceInfo(
    val resourceVersion: Int,
    val resourceBaseUrl: String,
    val resourceZip: String,
    val resourceList: List<FileEntry>,
    override val dateDownload: Date?
) : DownloadableCollection {
    constructor(productDto: ProductDto) : this(
        productDto.resourceVersion!!,
        productDto.resourceBaseUrl!!,
        productDto.resourceZip!!,
        productDto.resourceList!!.map { FileEntry(it, RESOURCE_FOLDER) },
        null
    )

    override fun getDownloadDate(): Date? {
        return ResourceInfoRepository.getInstance().getDownloadStatus(this)
    }

    override fun setDownloadDate(date: Date?) {
        ResourceInfoRepository.getInstance().setDownloadStatus(this, date)
    }

    override fun getAllFiles(): List<FileEntry> {
        return resourceList
    }

    override fun getAllFileNames(): List<String> {
        return resourceList.map { it.name }
    }

    override suspend fun deleteFiles() {
        getAllFiles().forEach { FileHelper.getInstance().deleteFile(it) }
    }

    override fun getDownloadTag(): String {
        return RESOURCE_TAG
    }
}