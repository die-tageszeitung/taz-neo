package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable
import de.taz.app.android.singletons.Storable

@Serializable
data class FileEntryDto(
    override val name: String,
    override val storageType: StorageType,
    val moTime: Long,
    val sha256: String,
    val size: Long
): Storable

@Serializable
enum class StorageType {
    issue,
    global,
    resource
}
