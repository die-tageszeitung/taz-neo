package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.FileEntryDto
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.FileEntryOperations
import kotlinx.serialization.Serializable

const val GLOBAL_FOLDER = "global"

@Entity(tableName = "FileEntry")
@Serializable
data class FileEntry(
    @PrimaryKey override val name: String,
    override val storageType: StorageType,
    override val moTime: Long,
    override val sha256: String,
    override val size: Long,
    override val folder: String
): FileEntryOperations {

    constructor(fileEntryDto: FileEntryDto, folder: String) : this(
        name = fileEntryDto.name,
        storageType = fileEntryDto.storageType,
        moTime = fileEntryDto.moTime,
        sha256 = fileEntryDto.sha256,
        size = fileEntryDto.size,
        folder = FileEntryOperations.getStorageFolder(fileEntryDto.storageType, folder)
    )

    constructor(image: Image): this(
        name =image.name,
        storageType = image.storageType,
        moTime = image.moTime,
        sha256 = image.sha256,
        size = image.size,
        folder = image.folder
    )


}
