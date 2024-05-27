package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.api.models.SearchHit

object SearchHitMapper {
    fun from (searchHitDto: SearchHitDto) : SearchHit {
        val articleFileName = searchHitDto.article.articleHtml.name
        val authorList = searchHitDto.article.authorList?.map { AuthorMapper.from(it) } ?: emptyList()

        return SearchHit(
            articleFileName,
            searchHitDto.article.mediaSyncId,
            authorList,
            searchHitDto.article.onlineLink,
            searchHitDto.baseUrl,
            searchHitDto.snippet,
            searchHitDto.title,
            searchHitDto.teaser,
            searchHitDto.sectionTitle,
            searchHitDto.date,
            searchHitDto.articleHtml,
            searchHitDto.article.pdf?.name,
        )
    }
}