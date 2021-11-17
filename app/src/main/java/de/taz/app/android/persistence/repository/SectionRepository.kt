package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.*
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.app.android.persistence.join.SectionImageJoin
import de.taz.app.android.persistence.join.SectionNavButtonJoin
import de.taz.app.android.util.SingletonHolder
import io.sentry.Sentry
import java.util.*

@Mockable
class SectionRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<SectionRepository, Context>(::SectionRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val imageRepository = ImageRepository.getInstance(applicationContext)

    fun save(section: Section) {
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
        imageRepository.save(section.imageList)
        appDatabase.sectionImageJoinDao()
            .insertOrReplace(section.imageList.mapIndexed { index, fileEntry ->
                SectionImageJoin(section.sectionHtml.name, fileEntry.name, index)
            })

        imageRepository.save(section.navButton)

        appDatabase.sectionNavButtonJoinDao().insertOrReplace(
            SectionNavButtonJoin(
                sectionFileName = section.sectionHtml.name,
                navButtonFileName = section.navButton.name
            )
        )

    }

    fun update(sectionStub: SectionStub) {
        appDatabase.sectionDao().update(sectionStub)
    }

    fun getStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().get(sectionFileName)
    }

    fun getStubLiveData(sectionFileName: String): LiveData<SectionStub?> {
        return appDatabase.sectionDao().getLiveData(sectionFileName)
    }

    fun getLiveData(sectionFileName: String): LiveData<Section?> {
        return Transformations.map(getStubLiveData(sectionFileName)) {
            it?.let {
                sectionStubToSection(it)
            }
        }
    }

    fun get(sectionFileName: String): Section? {
        return getStub(sectionFileName)?.let {
            sectionStubToSection(it)
        }
    }

    fun getSectionStubForArticle(articleFileName: String): SectionStub? {
        return appDatabase.sectionArticleJoinDao().getSectionStubForArticleFileName(articleFileName)
    }

    fun getNextSectionStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().getNext(sectionFileName)
    }

    fun getSectionStubsForIssue(
        issueKey: IssueKey
    ): List<SectionStub> {
        return appDatabase.sectionDao().getSectionsForIssue(
            issueKey.feedName, issueKey.date, issueKey.status
        )
    }

    fun getPreviousSectionStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().getPrevious(sectionFileName)
    }

    fun imagesForSectionStub(sectionFileName: String): List<Image> {
        return appDatabase.sectionImageJoinDao().getImagesForSection(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun sectionStubToSection(sectionStub: SectionStub): Section? {
        val sectionFileName = sectionStub.sectionFileName
        val sectionFile = fileEntryRepository.get(sectionFileName) ?: return null

        val articles =
            appDatabase.sectionArticleJoinDao().getArticlesForSection(sectionFileName)
                ?.map(articleRepository::articleStubToArticle)
                ?: emptyList()

        val images = appDatabase.sectionImageJoinDao().getImagesForSection(sectionFileName)

        // TODO: Although we expect a navbutton to be existent consistency issues happened in the past. We log them for now
        val navButton =
            appDatabase.sectionNavButtonJoinDao().getNavButtonForSection(sectionFileName)

        if (navButton == null) {
            Sentry.captureMessage("Expected navbutton for $sectionFileName but found none")
        }


        return Section(
            sectionHtml = sectionFile,
            issueDate = sectionStub.issueDate,
            title = sectionStub.title,
            type = sectionStub.type,
            navButton = navButton!!,
            articleList = articles,
            imageList = images,
            extendedTitle = sectionStub.extendedTitle,
            dateDownload = sectionStub.dateDownload
        )

    }

    fun delete(section: Section) {
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
            articleRepository.deleteArticle(article)
        }

        fileEntryRepository.delete(section.sectionHtml)

        appDatabase.sectionImageJoinDao().delete(
            section.imageList.mapIndexed { index, fileEntry ->
                SectionImageJoin(section.sectionHtml.name, fileEntry.name, index)
            }
        )

        section.imageList.forEach {
            try {
                imageRepository.delete(it)
            } catch (e: SQLiteConstraintException) {
                log.warn("FileEntry ${it.name} not deleted, maybe still used by a bookmarked article?")
                // do not delete still used by (presumably bookmarked) article
            }
        }

        appDatabase.sectionNavButtonJoinDao().delete(
            SectionNavButtonJoin(
                section.sectionHtml.name,
                section.navButton.name
            )
        )

        // TODO: Atm we are skipping contraint errors assuming, some foreign key constraint
        // is still important enough to protect these things
        // However we should have confidence over how and we we delete stuff and should refactor
        // this to instead of catching constraints modifying the data model to behave predictably
        try {
            imageRepository.delete(section.navButton)
        } catch (e: SQLiteConstraintException) {
            // do not delete still used
        }

        try {
            appDatabase.sectionDao().delete(SectionStub(section))
        } catch (e: SQLiteConstraintException) {
            // do not delete still used
        }
    }

    fun getNavButtonForSection(sectionFileName: String): Image? {
        return appDatabase.sectionNavButtonJoinDao().getNavButtonForSection(sectionFileName)
    }

    fun getNavButtonForArticle(articleName: String): Image? {
        return getSectionStubForArticle(articleName)?.let {
            getNavButtonForSection(it.sectionFileName)
        }
    }


    fun isDownloadedLiveData(sectionOperations: SectionOperations): LiveData<Boolean> {
        return appDatabase.sectionDao().isDownloadedLiveData(sectionOperations.key)
    }

    fun setDownloadDate(sectionStub: SectionStub, date: Date?) {
        update(sectionStub.copy(dateDownload = date))
    }

    fun getDownloadDate(sectionStub: SectionStub): Date? {
        return appDatabase.sectionDao().getDownloadDate(sectionStub.sectionFileName)
    }

}

