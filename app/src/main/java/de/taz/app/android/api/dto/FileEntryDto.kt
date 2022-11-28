package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FileEntryDto(
    val name: String,
    val storageType: StorageTypeDto,
    val moTime: Long,
    val sha256: String,
    val size: Long
)