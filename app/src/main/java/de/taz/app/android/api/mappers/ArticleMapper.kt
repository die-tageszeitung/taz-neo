package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.ArticleDto
import de.taz.app.android.api.dto.ImageResolutionDto
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ArticleType
import de.taz.app.android.api.models.AudioStub
import de.taz.app.android.api.models.AudioWithFile
import de.taz.app.android.api.models.AuthorJoinWithFile
import de.taz.app.android.api.models.ImageWithFile
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.repository.IssueKey

object ArticleMapper {

    fun from(issueKey: IssueKey, articleDto: ArticleDto, articleType: ArticleType?): Article {
        return Article(
            ArticleStub(issueKey, articleDto, articleType),
            articleHtml = FileEntryMapper.from(issueKey, articleDto.articleHtml),
            pdf = articleDto.pdf?.let { FileEntryMapper.from(issueKey, it) },
            audioWithFile = articleDto.audio?.let { AudioMapper.from(issueKey, it) }
                ?.let { AudioWithFile(AudioStub(it), it.file) },
            imagesWithFiles = articleDto.imageList?.map {
                ImageWithFile(ImageMapper.from(issueKey, it))
            } ?: emptyList(),
            authorJoins = (articleDto.authorList?.map { AuthorMapper.from(it) }
                ?: emptyList()).mapIndexed { index, it ->
                AuthorJoinWithFile(
                    ArticleAuthorImageJoin(
                        articleDto.articleHtml.name,
                        it.name,
                        it.imageAuthor?.name,
                        index
                    ), it.imageAuthor
                )
            },
            iconWithFile = articleDto.iconList?.firstOrNull { it.resolution == ImageResolutionDto.normal }
                ?.let { ImageWithFile(ImageMapper.from(issueKey, it)) },
        )
    }
}