package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.app.android.persistence.join.SectionImageJoin
import de.taz.app.android.util.SingletonHolder
import java.util.Date


class SectionRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<SectionRepository, Context>(::SectionRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val imageRepository = ImageRepository.getInstance(applicationContext)
    private val audioRepository = AudioRepository.getInstance(applicationContext)

    private val defaultNavDrawerFileName =
        applicationContext.getString(R.string.DEFAULT_NAV_DRAWER_FILE_NAME)

    /**
     * Save the [Section] to the database and replace any existing [Section] with the same key.
     *
     * This will recursively save all the related models.
     *
     * This method must be called as part of a transaction, for example when saving an [Issue].
     * As there are many-to-many relations, replacing an existing [Section] might result in some
     * orphaned children that have to be cleanup up by some scrubber process.
     */
    suspend fun saveInternal(section: Section) {
        val sectionFileName = section.sectionHtml.name

        // [SectionStub.podcastFileName] references the [AudioStub.fileName] as a ForeignKey,
        // thus the [AudioStub] must be saved before the [SectionStub] to fulfill the constraint.
        section.podcast?.let { audio ->
            audioRepository.saveInternal(audio)
        }

        appDatabase.sectionDao().insertOrReplace(SectionStub(section))
        fileEntryRepository.save(section.sectionHtml)

        section.articleList.forEach { articleRepository.saveInternal(it) }
        appDatabase.sectionArticleJoinDao().apply {
            deleteRelationToSection(sectionFileName)
            insertOrReplace(section.articleList.mapIndexed { index, article ->
                SectionArticleJoin(
                    section.sectionHtml.name, article.articleHtml.name, index
                )
            })
        }

        imageRepository.saveInternal(section.imageList)
        appDatabase.sectionImageJoinDao().apply {
            deleteRelationToSection(sectionFileName)
            insertOrReplace(section.imageList.mapIndexed { index, fileEntry ->
                SectionImageJoin(section.sectionHtml.name, fileEntry.name, index)
            })
        }
    }

    suspend fun update(sectionStub: SectionStub) {
        appDatabase.sectionDao().update(sectionStub)
    }

    suspend fun getStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().get(sectionFileName)
    }

    suspend fun get(sectionFileName: String): Section? {
        return getStub(sectionFileName)?.let {
            sectionStubToSection(it)
        }
    }

    suspend fun getSectionStubForArticle(articleFileName: String): SectionStub? {
        return appDatabase.sectionArticleJoinDao().getSectionStubForArticleFileName(articleFileName)
    }

    suspend fun getNextSectionStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().getNext(sectionFileName)
    }

    suspend fun getSectionStubsForIssue(
        issueKey: IssueKey
    ): List<SectionStub> {
        return appDatabase.sectionDao().getSectionsForIssue(
            issueKey.feedName, issueKey.date, issueKey.status
        )
    }

    suspend fun getSectionsForIssue(issueKey: IssueKey): List<Section> {
        return appDatabase.sectionDao().getSectionsForIssue(
            issueKey.feedName, issueKey.date, issueKey.status
        ).mapNotNull { sectionStubToSection(it) }
    }

    suspend fun getPreviousSectionStub(sectionFileName: String): SectionStub? {
        return appDatabase.sectionDao().getPrevious(sectionFileName)
    }

    suspend fun imagesForSectionStub(sectionFileName: String): List<Image> {
        return appDatabase.sectionImageJoinDao().getImagesForSection(sectionFileName)
    }

    suspend fun sectionStubToSection(sectionStub: SectionStub): Section? {
        val sectionFileName = sectionStub.sectionFileName
        val sectionFile = fileEntryRepository.get(sectionFileName) ?: return null

        val articles =
            appDatabase.sectionArticleJoinDao().getArticlesForSection(sectionFileName)
                ?.map { articleRepository.articleStubToArticle(it) }
                ?: emptyList()

        val images = appDatabase.sectionImageJoinDao().getImagesForSection(sectionFileName)

        val navButton = requireNotNull(imageRepository.get(defaultNavDrawerFileName)) {
            "navigation button is essential for the app running"
        }

        val podcast = sectionStub.podcastFileName?.let { audioRepository.get(it)  }

        return Section(
            sectionHtml = sectionFile,
            issueDate = sectionStub.issueDate,
            title = sectionStub.title,
            type = sectionStub.type,
            navButton = navButton,
            articleList = articles,
            imageList = images,
            extendedTitle = sectionStub.extendedTitle,
            dateDownload = sectionStub.dateDownload,
            podcast = podcast,
        )

    }

    suspend fun delete(section: Section) {
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

        // After the section has been deleted (and the foreign key reference is removed), we try to delete the audio entry
        section.podcast?.let { audio ->
            audioRepository.tryDelete(audio)
        }
    }

    suspend fun setDownloadDate(sectionStub: SectionStub, date: Date?) {
        update(sectionStub.copy(dateDownload = date))
    }

    suspend fun getDownloadDate(sectionStub: SectionStub): Date? {
        return appDatabase.sectionDao().getDownloadDate(sectionStub.sectionFileName)
    }

}

