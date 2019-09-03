package de.taz.app.android.persistence.repository

import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionBase
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.app.android.persistence.join.SectionImageJoin

class SectionRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance())  {

    private val articleRepository = ArticleRepository(appDatabase)

    fun save(section: Section) {
        appDatabase.sectionDao().insertOrReplace(SectionBase(section))
        appDatabase.fileEntryDao().insertOrReplace(section.sectionHtml)
        section.articleList.forEach { articleRepository.save(it) }
        appDatabase.sectionArticleJoinDao().insertOrReplace(
            section.articleList.map { article ->
                SectionArticleJoin(
                    section.sectionHtml.name,
                    article.articleHtml.name
                )
            }
        )
        appDatabase.fileEntryDao().insertOrReplace(section.imageList)
        appDatabase.sectionImageJoinDao().insertOrReplace(section.imageList.map {
            SectionImageJoin(section.sectionHtml.name, it.name)
        })
    }

    fun getBase(sectionFileName: String): SectionBase {
        return appDatabase.sectionDao().get(sectionFileName)
    }

    fun get(sectionFileName: String): Section {
        val sectionBase = getBase(sectionFileName)
        val sectionFile = appDatabase.fileEntryDao().getByName(sectionFileName)

        val articles = appDatabase.sectionArticleJoinDao().getArticleFileNamesForSection(sectionFileName)?.let {
            articleRepository.get(it)
        } ?: listOf()

        val images = appDatabase.sectionImageJoinDao().getImagesForSection(sectionFileName)

        return Section(
            sectionFile,
            sectionBase.title,
            sectionBase.type,
            articles,
            images
        )
    }
}