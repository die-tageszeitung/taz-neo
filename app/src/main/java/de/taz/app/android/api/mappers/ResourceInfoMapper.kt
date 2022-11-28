package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.api.models.ResourceInfo

object ResourceInfoMapper {
    fun from(productDto: ProductDto): ResourceInfo {
        return ResourceInfo(
            requireNotNull(productDto.resourceVersion),
            requireNotNull(productDto.resourceBaseUrl),
            requireNotNull(productDto.resourceZip),
            requireNotNull(productDto.resourceList).map {
                FileEntryMapper.from(it)
            },
            null
        )
    }
}