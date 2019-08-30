package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import de.taz.app.android.api.dto.ProductDto

@Entity(tableName = "AppInfo")
class AppInfo (
    @PrimaryKey val appName: AppName,
    val globalBaseUrl: String,
    val appType: AppType
) {
    constructor(productDto: ProductDto): this(
        productDto.appName!!,
        productDto.globalBaseUrl!!,
        productDto.appType!!
    )

}

