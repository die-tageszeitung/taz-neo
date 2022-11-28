package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.ImageDto
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.Image
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.StorageService
import java.io.File

object ImageMapper {
    fun from(issueKey: IssueKey, imageDto: ImageDto): Image {
        val storageType = StorageTypeMapper.from(imageDto.storageType)
        val path = StorageService.determineFilePath(storageType, imageDto.name, issueKey)
        val folder = requireNotNull(File(path).parent)

        return Image(
            name = imageDto.name,
            storageType = storageType,
            moTime = imageDto.moTime,
            sha256 = imageDto.sha256,
            size = imageDto.size,
            path = path,
            folder = folder,
            type = ImageTypeMapper.from(imageDto.type),
            alpha = imageDto.alpha,
            resolution = ImageResolutionMapper.from(imageDto.resolution),
            dateDownload = null,
            storageLocation = StorageLocation.NOT_STORED
        )
    }
}