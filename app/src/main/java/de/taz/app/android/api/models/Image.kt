package de.taz.app.android.api.models

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.dto.ImageDto
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.persistence.repository.FileEntryRepository
import kotlinx.serialization.Serializable

@Serializable
data class Image(
    override val name: String,
    override val storageType: StorageType,
    override val moTime: Long,
    override val sha256: String,
    override val size: Long,
    override val folder: String,
    val type: ImageType,
    val alpha: Float,
    val resolution: ImageResolution,
    override val downloadedStatus: DownloadStatus?
):  FileEntryOperations {

    constructor(imageDto: ImageDto, folder: String) : this(
        name = imageDto.name,
        storageType = imageDto.storageType,
        moTime = imageDto.moTime,
        sha256 = imageDto.sha256,
        size = imageDto.size,
        folder = FileEntryOperations.getStorageFolder(imageDto.storageType, folder),
        type = imageDto.type,
        alpha = imageDto.alpha,
        resolution = imageDto.resolution,
        downloadedStatus = DownloadStatus.pending
    )

    constructor(fileEntry: FileEntry, imageStub: ImageStub) : this(
        name = fileEntry.name,
        storageType = fileEntry.storageType,
        moTime = fileEntry.moTime,
        sha256 = fileEntry.sha256,
        size = fileEntry.size,
        folder = fileEntry.folder,
        type = imageStub.type,
        alpha = imageStub.alpha,
        resolution = imageStub.resolution,
        downloadedStatus = fileEntry.downloadedStatus
    )

    override fun setDownloadStatus(downloadStatus: DownloadStatus) {
        FileEntryRepository.getInstance().update(
            FileEntry(this).copy(downloadedStatus = downloadedStatus)
        )
    }

    override fun isDownloadedLiveData(applicationContext: Context?): LiveData<Boolean> {
        return FileEntryRepository.getInstance(applicationContext).isDownloadedLiveData(this.name)
    }

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