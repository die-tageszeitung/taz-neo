package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ProductDto

class ResourceInfo(
    val resourceVersion: Int,
    val resourceBaseUrl: String,
    val resourceZip: String,
    val resourceList: List<FileEntry>
) {
    constructor(productDto: ProductDto) : this (
        productDto.resourceVersion!!,
        productDto.resourceBaseUrl!!,
        productDto.resourceZip!!,
        productDto.resourceList!!
    )
}