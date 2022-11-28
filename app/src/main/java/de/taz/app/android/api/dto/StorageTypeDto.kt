package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable
enum class StorageTypeDto {
    issue,
    global,
    resource,
    UNKNOWN
}

object StorageTypeDtoEnumSerializer :
    EnumSerializer<StorageTypeDto>(StorageTypeDto.values(), StorageTypeDto.UNKNOWN)