package de.taz.app.android.api.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.StorageLocation
import java.util.*

const val GLOBAL_FOLDER = "global"

@Entity(tableName = "FileEntry")
data class FileEntry(
    @PrimaryKey override val name: String,
    override val storageType: StorageType,
    override val moTime: Long,
    override val sha256: String,
    override val size: Long,
    @Deprecated("folder field deprecated, file path now stored in path")
    override val folder: String,
    override val dateDownload: Date?,
    @ColumnInfo(defaultValue = "")
    override val path: String,
    override val storageLocation: StorageLocation
) : FileEntryOperations {


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
