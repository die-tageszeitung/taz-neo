package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.persistence.repository.IssueRepository
import java.util.*

data class Image(
    override val name: String,
    override val storageType: StorageType,
    override val moTime: Long,
    override val sha256: String,
    override val size: Long,
    @Deprecated("folder field deprecated, file path now stored in path")
    override val folder: String,
    override val path: String,
    val type: ImageType,
    val alpha: Float,
    val resolution: ImageResolution,
    override val dateDownload: Date?,
    override val storageLocation: StorageLocation
) : FileEntryOperations {

    constructor(fileEntry: FileEntry, imageStub: ImageStub) : this(
        name = fileEntry.name,
        storageType = fileEntry.storageType,
        moTime = fileEntry.moTime,
        sha256 = fileEntry.sha256,
        size = fileEntry.size,
        folder = fileEntry.folder,
        path = fileEntry.path,
        type = imageStub.type,
        alpha = imageStub.alpha,
        resolution = imageStub.resolution,
        dateDownload = fileEntry.dateDownload,
        storageLocation = fileEntry.storageLocation
    )

    suspend fun getIssueStub(applicationContext: Context): IssueStub =
        IssueRepository.getInstance(applicationContext).getIssueStubForImage(this@Image)
}

enum class ImageType {
    button,
    picture,
    advertisement,
    facsimile
}

enum class ImageResolution {
    small,
    normal,
    high
}