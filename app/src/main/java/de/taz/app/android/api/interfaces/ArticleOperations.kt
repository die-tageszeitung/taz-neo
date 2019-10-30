package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.SectionRepository

interface ArticleOperations {

    val articleFileName: String

    fun nextArticleStub(): ArticleStub? {
        return ArticleRepository.getInstance().nextArticleStub(articleFileName)
    }

    fun previousArticleStub(): ArticleStub? {
        return ArticleRepository.getInstance().previousArticleStub(articleFileName)
    }

    fun previousArticle(): Article? {
        return ArticleRepository.getInstance().previousArticle(articleFileName)
    }

    fun nextArticle(): Article? {
        return ArticleRepository.getInstance().nextArticle(articleFileName)
    }

    fun getSectionBase(): SectionStub? {
        return SectionRepository.getInstance().getSectionBaseForArticle(articleFileName)
    }

    fun getSection(): Section? {
        return SectionRepository.getInstance().getSectionForArticle(articleFileName)
    }

    fun getIndexInSection(): Int? {
        return ArticleRepository.getInstance().getIndexInSection(articleFileName)
    }
}