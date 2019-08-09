package de.taz.app.android.api.models

import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import de.taz.app.android.api.dto.ProductDto

class AppInfo(productDto: ProductDto) {
    val appName: AppName = productDto.appName!!
    val appType: AppType = productDto.appType!!
}