package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.AppTypeDto
import de.taz.app.android.api.dto.AppTypeDto.UNKNOWN
import de.taz.app.android.api.dto.AppTypeDto.local
import de.taz.app.android.api.dto.AppTypeDto.production
import de.taz.app.android.api.dto.AppTypeDto.test
import de.taz.app.android.api.models.AppType

object AppTypeMapper {
    fun from(appTypeDto: AppTypeDto): AppType = when(appTypeDto) {
        production -> AppType.production
        test -> AppType.test
        local -> AppType.local
        UNKNOWN -> throw UnknownEnumValueException("Can not map UNKNOWN AppTypeDto to a AppType")
    }
}