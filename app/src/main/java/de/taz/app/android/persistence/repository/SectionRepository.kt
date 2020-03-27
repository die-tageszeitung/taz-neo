package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.*
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.app.android.persistence.join.SectionImageJoin
import de.taz.app.android.persistence.join.SectionNavButtonJoin
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers

@Mockable
class SectionRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<SectionRepository, Context>(::SectionRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

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
        fileEntryRepository.save(section.imageList)
        appDatabase.sectionImageJoinDao()
            .insertOrReplace(section.imageList.mapIndexed { index, fileEntry ->
                SectionImageJoin(section.sectionHtml.name, fileEntry.name, index)
            })

        appDatabase.imageDao().insertOrReplace(section.navButton)

        appDatabase.sectionNavButtonJoinDao().insertOrReplace(
            SectionNavButtonJoin(
                sectionFileName = section.sectionHtml.name,
                navButtonFileName = section.navButton.name,
                navButtonStorageType = section.navButton.storageType
            )
        )

    }

    fun getStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().get(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun getStubOrThrow(sectionFileName: String): SectionStub {
        return getStub(sectionFileName) ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(sectionFileName: String): Section {
        return sectionStubToSection(getStubOrThrow(sectionFileName))
    }

    fun getLiveData(sectionFileName: String): LiveData<Section?> {
        return appDatabase.sectionDao().getLiveData(sectionFileName).switchMap { input ->
            liveData(Dispatchers.IO) {
                input?.let { emit(sectionStubToSection(input)) } ?: emit(null)
            }
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

    fun getSectionStubsLiveDataForIssue(issueOperations: IssueOperations) =
        getSectionStubsLiveDataForIssue(
            issueOperations.feedName,
            issueOperations.date,
            issueOperations.status
        )

    fun getSectionStubsLiveDataForIssue(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): List<SectionStub> {
        return appDatabase.sectionDao().getSectionsForIssue(
            issueFeedName, issueDate, issueStatus
        )
    }

    fun getSectionsForIssue(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ) = getSectionStubsForIssue(
        issueFeedName,
        issueDate,
        issueStatus
    ).map { sectionStubToSection(it) }

    fun getSectionStubsForIssue(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): List<SectionStub> {
        return appDatabase.sectionDao().getSectionsForIssue(
            issueFeedName, issueDate, issueStatus
        )
    }


    fun getSectionStubsForIssueOperations(issueOperations: IssueOperations) =
        getSectionStubsForIssue(
            issueOperations.feedName, issueOperations.date, issueOperations.status
        )

    fun getAllSectionStubsForSectionName(sectionName: String): List<SectionStub> {
        return appDatabase.sectionDao().getAllSectionStubsForSectionName(sectionName)
    }

    fun getAllSectionsForSectionName(sectionFileName: String) =
        getAllSectionStubsForSectionName(sectionFileName).map { sectionStubToSection(it) }

    @Throws(NotFoundException::class)
    fun getNextSection(sectionFileName: String): Section? =
        getNextSectionStub(sectionFileName)?.let { sectionStubToSection(it) }

    @Throws(NotFoundException::class)
    fun getNextSection(section: SectionOperations): Section? =
        getNextSection(section.key)

    fun getPreviousSectionStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().getPrevious(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun getPreviousSection(sectionFileName: String): Section? =
        getPreviousSectionStub(sectionFileName)?.let { sectionStubToSection(it) }

    @Throws(NotFoundException::class)
    fun getPreviousSection(section: SectionOperations): Section? =
        getPreviousSection(section.key)

    fun imagesForSectionStub(sectionFileName: String): List<FileEntry> {
        return appDatabase.sectionImageJoinDao().getImagesForSection(sectionFileName)
    }

    fun imageNamesForSectionStub(sectionFileName: String): List<String> {
        return appDatabase.sectionImageJoinDao().getImageNamesForSection(sectionFileName)
    }

    @Throws(NotFoundException::class)
    fun sectionStubToSection(sectionStub: SectionStub): Section {
        val sectionFileName = sectionStub.sectionFileName
        val sectionFile = fileEntryRepository.getOrThrow(sectionFileName)

        val articles =
            appDatabase.sectionArticleJoinDao().getArticleFileNamesForSection(sectionFileName)
                ?.let {
                    articleRepository.getOrThrow(it)
                } ?: listOf()

        val images = appDatabase.sectionImageJoinDao().getImagesForSection(sectionFileName)

        val navButton =
            appDatabase.sectionNavButtonJoinDao().getNavButtonForSection(sectionFileName)

        images.let {
            return Section(
                sectionHtml = sectionFile,
                issueDate = sectionStub.issueDate,
                title = sectionStub.title,
                type = sectionStub.type,
                navButton = navButton,
                articleList = articles,
                imageList = images,
                extendedTitle = sectionStub.extendedTitle
            )
        }
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

        appDatabase.sectionNavButtonJoinDao().delete(
            SectionNavButtonJoin(
                section.sectionHtml.name,
                section.navButton.name,
                section.navButton.storageType
            )
        )

        try {
            appDatabase.imageDao().delete(section.navButton)
        } catch (e: SQLiteConstraintException) {
            log.warn("NavButton ${section.navButton} not deleted - pobably still used by another section")
        }

        appDatabase.sectionDao().delete(SectionStub(section))
    }

    fun getNavButton(sectionFileName: String): Image {
        return appDatabase.sectionNavButtonJoinDao().getNavButtonForSection(sectionFileName)
    }

}

