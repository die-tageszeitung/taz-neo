package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.api.interfaces.CacheableDownload

const val RESOURCE_FOLDER = "resources"
const val RESOURCE_TAG = "resources"

data class ResourceInfo(
    val resourceVersion: Int,
    val resourceBaseUrl: String,
    val resourceZip: String,
    val resourceList: List<FileEntry>
): CacheableDownload {
    constructor(productDto: ProductDto) : this (
        productDto.resourceVersion!!,
        productDto.resourceBaseUrl!!,
        productDto.resourceZip!!,
        productDto.resourceList!!.map { FileEntry(it, RESOURCE_FOLDER) }
    )

    override fun getAllFiles(): List<FileEntry> {
        return resourceList
    }

    override fun getDownloadTag(): String? {
        return RESOURCE_TAG
    }

}