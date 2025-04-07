package de.taz.app.android.api.mappers

import androidx.core.text.HtmlCompat
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
            HtmlCompat.fromHtml(searchHitDto.title, HtmlCompat.FROM_HTML_MODE_LEGACY).toString(),
            searchHitDto.teaser,
            searchHitDto.sectionTitle,
            searchHitDto.date,
            searchHitDto.articleHtml,
            searchHitDto.article.pdf?.name,
            searchHitDto.article.audio?.file?.name,
        )
    }
}