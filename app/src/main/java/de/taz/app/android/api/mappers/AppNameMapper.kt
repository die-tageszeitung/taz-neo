package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.AppNameDto
import de.taz.app.android.api.dto.AppNameDto.LMd
import de.taz.app.android.api.dto.AppNameDto.UNKNOWN
import de.taz.app.android.api.dto.AppNameDto.taz
import de.taz.app.android.api.models.AppName

object AppNameMapper {
    fun from(appNameDto: AppNameDto): AppName = when (appNameDto) {
        taz -> AppName.taz
        LMd -> AppName.lmd
        UNKNOWN -> throw UnknownEnumValueException("Can not map $appNameDto AppNameDto to a AppName")
    }
}