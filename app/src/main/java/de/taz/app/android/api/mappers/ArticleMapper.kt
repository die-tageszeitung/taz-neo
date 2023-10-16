package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.ArticleDto
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleType
import de.taz.app.android.persistence.repository.IssueKey

object ArticleMapper {

    fun from(issueKey: IssueKey, articleDto: ArticleDto, articleType: ArticleType): Article {
        return Article(
            FileEntryMapper.from(issueKey, articleDto.articleHtml),
            issueKey.feedName,
            issueKey.date,
            articleDto.title,
            articleDto.teaser,
            articleDto.onlineLink,
            articleDto.audio?.let { AudioMapper.from(issueKey, it) },
            articleDto.pageNameList ?: emptyList(),
            articleDto.imageList?.map { ImageMapper.from(issueKey, it) } ?: emptyList(),
            articleDto.authorList?.map { AuthorMapper.from(it) } ?: emptyList(),
            articleDto.mediaSyncId,
            articleDto.chars,
            articleDto.words,
            articleDto.readMinutes,
            articleType,
            bookmarkedTime = null,
            0,
            0,
            null
        )
    }
}