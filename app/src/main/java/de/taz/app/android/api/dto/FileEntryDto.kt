package de.taz.app.android.api.dto

import de.taz.app.android.api.interfaces.File
import de.taz.app.android.api.interfaces.StorageType

data class FileEntryDto(
    override val name: String,
    override val storageType: StorageType,
    override val moTime: Long,
    override val sha256: String,
    override val size: Long
) : File
