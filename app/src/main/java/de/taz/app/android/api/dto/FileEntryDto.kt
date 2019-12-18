package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FileEntryDto(
    val name: String,
    val storageType: StorageType,
    val moTime: Long,
    val sha256: String,
    val size: Long
)

@JsonClass(generateAdapter = false)
enum class StorageType {
    issue,
    global,
    public,
    resource
}
