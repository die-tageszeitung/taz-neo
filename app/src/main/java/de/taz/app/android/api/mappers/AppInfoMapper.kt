package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.api.models.AppInfo

object AppInfoMapper {
    fun from(productDto: ProductDto): AppInfo {
        return AppInfo(
            AppNameMapper.from(
                requireNotNull(productDto.appName)
            ),
            requireNotNull(productDto.globalBaseUrl),
            AppTypeMapper.from(
                requireNotNull(productDto.appType)
            ),
            requireNotNull(productDto.androidVersion)
        )
    }
}