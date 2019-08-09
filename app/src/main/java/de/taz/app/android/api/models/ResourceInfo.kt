package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ProductDto

class ResourceInfo(productDto: ProductDto) {
    val resourceVersion: Int = productDto.resourceVersion!!
    val resourceBaseUrl: String = productDto.resourceBaseUrl!!
    val resourceZip: String = productDto.resourceZip!!
    val resourceList: List<FileEntry> = productDto.resourceList!!
}