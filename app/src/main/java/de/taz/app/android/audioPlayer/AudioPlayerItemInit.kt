package de.taz.app.android.audioPlayer

import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.AbstractIssueKey

sealed interface AudioPlayerItemInit

data class IssueOfArticleInit(
    val issueKey: AbstractIssueKey,
    val articleStub: ArticleStub,
) : AudioPlayerItemInit

data class IssueInit(
    val issueStub: IssueStub
): AudioPlayerItemInit

data class ArticleInit(
    val articleStub: ArticleStub
): AudioPlayerItemInit