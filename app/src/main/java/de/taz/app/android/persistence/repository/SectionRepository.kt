package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.app.android.persistence.join.SectionImageJoin
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder

@Mockable
class SectionRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<SectionRepository, Context>(::SectionRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val log by Log

    @UiThread
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

    @UiThread
    fun getBase(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().get(sectionFileName)
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getBaseOrThrow(sectionFileName: String): SectionStub {
        return getBase(sectionFileName) ?: throw NotFoundException()
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getOrThrow(sectionFileName: String): Section {
        return sectionStubToSection(getBaseOrThrow(sectionFileName))
    }

    @UiThread
    fun getLiveData(sectionFileName: String): LiveData<Section?> {
        return Transformations.map(appDatabase.sectionDao().getLiveData(sectionFileName)) { input ->
            input?.let { sectionStubToSection(it) }
        }
    }

    @UiThread
    fun get(sectionFileName: String): Section? {
        return try {
            getOrThrow(sectionFileName)
        } catch (nfe: NotFoundException) {
            null
        }
    }

    @UiThread
    fun getSectionStubForArticle(articleFileName: String): SectionStub? {
        return appDatabase.sectionArticleJoinDao().getSectionStubForArticleFileName(articleFileName)
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getSectionForArticle(articleFileName: String): Section? {
        return getSectionStubForArticle(articleFileName)?.let { sectionStubToSection(it) }
    }

    @UiThread
    fun getNextSectionStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().getNext(sectionFileName)
    }

    @UiThread
    fun getSectionStubsForIssueOperations(issueOperations: IssueOperations): List<SectionStub> {
        return appDatabase.sectionDao().getSectionsForIssue(
            issueOperations.feedName, issueOperations.date, issueOperations.status
        )
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getNextSection(sectionFileName: String): Section? =
        getNextSectionStub(sectionFileName)?.let { sectionStubToSection(it) }

    @UiThread
    @Throws(NotFoundException::class)
    fun getNextSection(section: SectionOperations): Section? =
        getNextSection(section.sectionFileName)

    @UiThread
    fun getPreviousSectionStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().getPrevious(sectionFileName)
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getPreviousSection(sectionFileName: String): Section? =
        getPreviousSectionStub(sectionFileName)?.let { sectionStubToSection(it) }

    @UiThread
    @Throws(NotFoundException::class)
    fun getPreviousSection(section: SectionOperations): Section? =
        getPreviousSection(section.sectionFileName)

    @UiThread
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
                sectionStub.issueDate,
                sectionStub.title,
                sectionStub.type,
                articles,
                images,
                sectionStub.extendedTitle
            )
        } ?: throw NotFoundException()
    }

    @UiThread
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

            appDatabase.sectionImageJoinDao().delete(
                section.imageList.mapIndexed { index, fileEntry ->
                    SectionImageJoin(section.sectionHtml.name, fileEntry.name, index)
                }
            )

            section.imageList.forEach {
                try {
                    fileEntryRepository.delete(it)
                    log.debug("deleted FileEntry of image $it")
                } catch (e: SQLiteConstraintException) {
                    log.warn("FileEntry $it not deleted, maybe still used by a bookmarked article?")
                    // do not delete still used by (presumably bookmarked) article
                }
            }

            appDatabase.sectionDao().delete(SectionStub(section))
        }
    }
}

