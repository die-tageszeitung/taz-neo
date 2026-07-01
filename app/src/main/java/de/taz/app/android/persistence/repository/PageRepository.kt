package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.flow.Flow
import java.util.Date

class PageRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<PageRepository, Context>(::PageRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val audioRepository = AudioRepository.getInstance(applicationContext)

    suspend fun update(pageStub: PageStub) {
        appDatabase.pageDao().update(pageStub)
    }

    /**
     * Save the [Page] to the database and replace any existing [Page] with the same key.
     *
     * This will recursively save all the related models.
     *
     * This method must be called as part of a transaction, for example when saving an [Issue].
     */
    suspend fun saveInternal(page: Page) {
        // [PageStub.podcastFileName] references the [AudioStub.fileName] as a ForeignKey,
        // thus the [AudioStub] must be saved before the [PageStub] to fulfill the constraint.
        page.podcast?.let { audio ->
            audioRepository.saveInternal(audio)
        }

        appDatabase.pageDao().insertOrReplace(page.pageStub)
        fileEntryRepository.save(page.pagePdf)
    }

    /**
     * Save the downloaded front [Page] for the [issueKey].
     *
     * The [Issue] Metadata itself does not have to be stored yet, as a relation to the [issueKey]
     * is just saved via an [IssuePageJoin].
     * It is required to store the relation, so that the Front Page can be retrieved for a specific
     * Issue in the carousel.
     */
    suspend fun saveFrontPage(page: Page, issueKey: IssueKey): Page {
        return appDatabase.withTransaction {
            saveInternal(page)

            appDatabase.issuePageJoinDao().insertOrReplace(
                IssuePageJoin(
                    issueKey.feedName,
                    issueKey.date,
                    issueKey.status,
                    page.pdfFileName,
                    0
                )
            )

            requireNotNull(get(page.pdfFileName)) { "Could not get Page(${page.pdfFileName}) after it was saved" }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun getWithoutFile(fileName: String): Page? {
        return appDatabase.pageDao().get(fileName)
    }

    suspend fun getStub(fileName: String): PageStub? {
        return appDatabase.pageDao().get(fileName)?.pageStub
    }

    @Throws(NotFoundException::class)
    suspend fun getOrThrow(fileName: String): Page {
        return appDatabase.pageDao().get(fileName) ?: throw NotFoundException("Page $fileName not found in database")
    }

    suspend fun get(fileName: String): Page? {
        return try {
            getOrThrow(fileName)
        } catch (e: NotFoundException) {
            null
        }
    }

    suspend fun getFrontPage(issueKey: IssueKey): Page? {
        return appDatabase.issuePageJoinDao()
            .getFrontPageForIssue(issueKey.feedName, issueKey.date, issueKey.status)
    }

    suspend fun delete(page: Page) {
        appDatabase.pageDao().delete(page.pageStub)
        fileEntryRepository.delete(page.pdfFileName)
        page.podcast?.let { audioRepository.tryDelete(it) }
    }

    suspend fun delete(pages: List<Page>) {
        appDatabase.pageDao().delete(
            pages.map { it.pageStub }
        )

        fileEntryRepository.deleteList(pages.map { it.pdfFileName })
        pages.mapNotNull { it.podcast }.forEach {
            audioRepository.tryDelete(it)
        }
    }

    suspend fun deleteIfNoIssueRelated(pages: List<Page>) {
        appDatabase.pageDao().deletePageFileEntriesIfNoIssueRelated(pages.map { it.pdfFileName })
        appDatabase.pageDao().deleteIfNoIssueRelated(pages.map { it.pdfFileName })
        pages.mapNotNull { it.podcast }.forEach {
            audioRepository.tryDelete(it)
        }
    }

    suspend fun getPagesForIssueKey(issueKey: IssueKey): List<Page> {
        return appDatabase.pageDao()
            .getPageListForIssue(issueKey.feedName, issueKey.date, issueKey.status)
    }

    fun getPagesForIssueKeyFlow(issueKey: IssueKey): Flow<List<Page>> {
        return appDatabase.pageDao()
            .getPageFlowForIssue(issueKey.feedName, issueKey.date, issueKey.status)
    }
}
