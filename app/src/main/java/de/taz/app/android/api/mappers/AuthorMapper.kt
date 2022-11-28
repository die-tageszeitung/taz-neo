package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.AuthorDto
import de.taz.app.android.api.models.Author

object AuthorMapper {
    fun from(authorDto: AuthorDto): Author {
        return Author(
            authorDto.name,
            authorDto.imageAuthor?.let {
                FileEntryMapper.from(it)
            }
        )
    }
}