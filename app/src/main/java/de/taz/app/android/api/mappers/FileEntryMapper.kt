package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.FileEntryDto
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.StorageService
import java.io.File

object FileEntryMapper {
    fun from(fileEntryDto: FileEntryDto) = from(null, fileEntryDto)

    fun from(issueKey: IssueKey?, fileEntryDto: FileEntryDto): FileEntry {
        val storageType = StorageTypeMapper.from(fileEntryDto.storageType)
        val path = StorageService.determineFilePath(storageType, fileEntryDto.name, issueKey)
        val folder = requireNotNull(File(path).parent)

        return FileEntry(
            name = fileEntryDto.name,
            storageType = storageType,
            moTime = fileEntryDto.moTime,
            sha256 = fileEntryDto.sha256,
            size = fileEntryDto.size,
            path = path,
            folder = folder,
            dateDownload = null,
            storageLocation = StorageLocation.NOT_STORED
        )
    }
}