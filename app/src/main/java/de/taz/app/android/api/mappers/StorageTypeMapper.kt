package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.StorageTypeDto
import de.taz.app.android.api.dto.StorageTypeDto.UNKNOWN
import de.taz.app.android.api.dto.StorageTypeDto.global
import de.taz.app.android.api.dto.StorageTypeDto.issue
import de.taz.app.android.api.dto.StorageTypeDto.resource
import de.taz.app.android.api.models.StorageType

object StorageTypeMapper {
    fun from(storageTypeDto: StorageTypeDto): StorageType = when (storageTypeDto) {
        issue -> StorageType.issue
        global -> StorageType.global
        resource -> StorageType.resource
        UNKNOWN -> throw UnknownEnumValueException("Can not map UNKNOWN StorageTypeDto to a StorageType")
    }
}