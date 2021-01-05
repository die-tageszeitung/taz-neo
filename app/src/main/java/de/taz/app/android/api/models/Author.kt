package de.taz.app.android.api.models

import de.taz.app.android.api.dto.AuthorDto
import de.taz.app.android.singletons.StorageService

data class Author(
    val name: String? = null,
    val imageAuthor: FileEntry? = null
) {

    constructor(authorDto: AuthorDto, storageService: StorageService) : this(
        authorDto.name,
        authorDto.imageAuthor?.let { FileEntry(it, storageService.determineFilePath(it, null)) }
    )
}