package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ProductDto

data class ResourceInfo(
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as ResourceInfo

        return resourceVersion == other.resourceVersion &&
                resourceBaseUrl == other.resourceBaseUrl &&
                resourceZip == other.resourceZip &&
                resourceList.containsAll(other.resourceList) &&
                other.resourceList.containsAll(resourceList)

    }
}