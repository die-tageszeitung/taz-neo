package de.taz.app.android.api.models

import de.taz.app.android.api.dto.AuthorDto
import de.taz.app.android.api.interfaces.CacheableDownload

data class Author(
    val name: String? = null,
    val imageAuthor: FileEntry? = null
) {

    constructor(authorDto: AuthorDto) : this(
        authorDto.name,
        authorDto.imageAuthor?.let { FileEntry(it, GLOBAL_FOLDER) }
    )
}