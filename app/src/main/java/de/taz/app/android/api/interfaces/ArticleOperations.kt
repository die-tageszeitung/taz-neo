package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionBase
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.SectionRepository

interface ArticleOperations {

    val articleFileName: String

    fun nextArticleBase(): ArticleBase? {
        return ArticleRepository.getInstance().nextArticleBase(articleFileName)
    }

    fun previousArticleBase(): ArticleBase? {
        return ArticleRepository.getInstance().previousArticleBase(articleFileName)
    }

    fun previousArticle(): Article? {
        return ArticleRepository.getInstance().previousArticle(articleFileName)
    }

    fun nextArticle(): Article? {
        return ArticleRepository.getInstance().nextArticle(articleFileName)
    }

    fun getSectionBase(): SectionBase? {
        return SectionRepository.getInstance().getSectionBaseForArticle(articleFileName)
    }

    fun getSection(): Section? {
        return SectionRepository.getInstance().getSectionForArticle(articleFileName)
    }

    fun getIndexInSection(): Int? {
        return ArticleRepository.getInstance().getIndexInSection(articleFileName)
    }
}