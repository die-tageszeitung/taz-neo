package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass
import de.taz.app.android.singletons.Storable

@JsonClass(generateAdapter = true)
data class FileEntryDto(
    override val name: String,
    override val storageType: StorageType,
    val moTime: Long,
    val sha256: String,
    val size: Long
): Storable

@JsonClass(generateAdapter = false)
enum class StorageType {
    issue,
    global,
    public,
    resource
}
