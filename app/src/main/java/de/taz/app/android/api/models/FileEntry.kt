package de.taz.app.android.api.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.FileEntryDto
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.persistence.serializers.DateSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.io.File
import java.util.*

const val GLOBAL_FOLDER = "global"

@ExperimentalSerializationApi
@Entity(tableName = "FileEntry")
@Serializable(with=DateSerializer::class)
data class FileEntry(
    @PrimaryKey override val name: String,
    override val storageType: StorageType,
    override val moTime: Long,
    override val sha256: String,
    override val size: Long,
    override val folder: String,
    override val dateDownload: Date?,
    @ColumnInfo(defaultValue = "")
    override val path: String,
    override val storageLocation: StorageLocation
) : FileEntryOperations {

    constructor(fileEntryDto: FileEntryDto, path: String) : this(
        name = fileEntryDto.name,
        storageType = fileEntryDto.storageType,
        moTime = fileEntryDto.moTime,
        sha256 = fileEntryDto.sha256,
        size = fileEntryDto.size,
        path = path,
        folder = File(path).parent!!,
        dateDownload = null,
        storageLocation = StorageLocation.NOT_STORED
    )

    constructor(image: Image) : this(
        name = image.name,
        storageType = image.storageType,
        moTime = image.moTime,
        sha256 = image.sha256,
        size = image.size,
        path = image.path,
        folder = image.folder,
        dateDownload = image.dateDownload,
        storageLocation = image.storageLocation
    )
}
