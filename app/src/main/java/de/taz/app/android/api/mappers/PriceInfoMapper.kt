package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.PriceInfoDto
import de.taz.app.android.api.models.PriceInfo

object PriceInfoMapper {
    fun from(priceInfoDto: PriceInfoDto): PriceInfo {
        return PriceInfo(
            priceInfoDto.name,
            priceInfoDto.price
        )
    }
}