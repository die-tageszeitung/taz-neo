package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Moment
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.app.android.persistence.join.SectionImageJoin
import de.taz.app.android.util.SingletonHolder

class SectionRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<SectionRepository, Context>(::SectionRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    fun save(section: Section) {
        appDatabase.runInTransaction {
            appDatabase.sectionDao().insertOrReplace(SectionStub(section))
            fileEntryRepository.save(section.sectionHtml)
            section.articleList.forEach { articleRepository.save(it) }
            appDatabase.sectionArticleJoinDao().insertOrReplace(
                section.articleList.mapIndexed { index, article ->
                    SectionArticleJoin(
                        section.sectionHtml.name,
                        article.articleHtml.name,
                        index
                    )
                }
            )
            fileEntryRepository.save(section.imageList)
            appDatabase.sectionImageJoinDao()
                .insertOrReplace(section.imageList.mapIndexed { index, fileEntry ->
                    SectionImageJoin(section.sectionHtml.name, fileEntry.name, index)
                })
        }
    }

    fun getBase(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().get(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun getBaseOrThrow(sectionFileName: String): SectionStub {
        return getBase(sectionFileName) ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(sectionFileName: String): Section {
        return sectionStubToSection(getBaseOrThrow(sectionFileName))
    }

    fun getLiveData(sectionFileName: String): LiveData<Section?> {
        return Transformations.map(appDatabase.sectionDao().getLiveData(sectionFileName)) { input ->
            input?.let { sectionStubToSection(it) }
        }
    }

    fun get(sectionFileName: String): Section? {
        return try {
            getOrThrow(sectionFileName)
        } catch (nfe: NotFoundException) {
            null
        }
    }

    fun getSectionStubForArticle(articleFileName: String): SectionStub? {
        return appDatabase.sectionArticleJoinDao().getSectionStubForArticleFileName(articleFileName)
    }

    @Throws(NotFoundException::class)
    fun getSectionForArticle(articleFileName: String): Section? {
        return getSectionStubForArticle(articleFileName)?.let { sectionStubToSection(it) }
    }

    fun getNextSectionStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().getNext(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun getNextSection(sectionFileName: String): Section? =
        getNextSectionStub(sectionFileName)?.let { sectionStubToSection(it) }

    @Throws(NotFoundException::class)
    fun getNextSection(section: SectionOperations): Section? =
        getNextSection(section.sectionFileName)

    fun getPreviousSectionStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().getPrevious(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun getPreviousSection(sectionFileName: String): Section? =
        getPreviousSectionStub(sectionFileName)?.let { sectionStubToSection(it) }

    @Throws(NotFoundException::class)
    fun getPreviousSection(section: SectionOperations): Section? =
        getPreviousSection(section.sectionFileName)

    @Throws(NotFoundException::class)
    fun sectionStubToSection(sectionStub: SectionStub): Section {
        val sectionFileName = sectionStub.sectionFileName
        val sectionFile = fileEntryRepository.getOrThrow(sectionFileName)

        val articles =
            appDatabase.sectionArticleJoinDao().getArticleFileNamesForSection(sectionFileName)?.let {
                articleRepository.getOrThrow(it)
            } ?: listOf()

        val images = appDatabase.sectionImageJoinDao().getImagesForSection(sectionFileName)

        images?.let {
            return Section(
                sectionFile,
                sectionStub.date,
                sectionStub.title,
                sectionStub.type,
                articles,
                images
            )
        } ?: throw NotFoundException()
    }

    fun delete(section: Section) {
        appDatabase.runInTransaction {
            appDatabase.sectionArticleJoinDao().delete(
                section.articleList.mapIndexed { index, article ->
                    SectionArticleJoin(
                        section.sectionHtml.name,
                        article.articleHtml.name,
                        index
                    )
                }
            )
            section.articleList.forEach { article ->
                if (!article.bookmarked) {
                    articleRepository.delete(article)
                }
            }


            fileEntryRepository.delete(section.sectionHtml)

            appDatabase.sectionImageJoinDao()
                .delete(section.imageList.mapIndexed { index, fileEntry ->
                    SectionImageJoin(section.sectionHtml.name, fileEntry.name, index)
                })
            // TODO delete files only if not part of bookmarked article
            fileEntryRepository.delete(section.imageList)

            appDatabase.sectionDao().delete(SectionStub(section))
        }
    }
}

