package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.util.SingletonHolder
import java.util.*

class PageRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<PageRepository, Context>(::PageRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    suspend fun update(pageStub: PageStub) {
        appDatabase.pageDao().update(pageStub)
    }

    suspend fun save(page: Page, issueKey: IssueKey): Page {
        appDatabase.pageDao().insertOrReplace(
            PageStub(
                page.pagePdf.name,
                page.title,
                page.pagina,
                page.type,
                page.frameList,
                page.dateDownload,
                page.baseUrl
            )
        )
        fileEntryRepository.save(page.pagePdf)
        appDatabase.issuePageJoinDao().insertOrReplace(
            IssuePageJoin(
                issueKey.feedName,
                issueKey.date,
                issueKey.status,
                page.pagePdf.name,
                0
            )
        )
        return get(page.pagePdf.name)!!
    }

    suspend fun save(pages: List<Page>, issueKey: IssueKey) {
        pages.forEach { page ->
            save(page, issueKey)
        }
    }

    suspend fun getWithoutFile(fileName: String): PageStub? {
        return appDatabase.pageDao().get(fileName)
    }

    suspend fun getStub(fileName: String): PageStub? {
        return appDatabase.pageDao().get(fileName)
    }

    @Throws(NotFoundException::class)
    suspend fun getOrThrow(fileName: String): Page {
        appDatabase.pageDao().get(fileName)?.let {
            return pageStubToPage(it)
        } ?: throw NotFoundException()
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
            .getFrontPageForIssue(issueKey.feedName, issueKey.date, issueKey.status)?.let {
                pageStubToPage(it)
            }
    }

    private suspend fun pageStubToPage(pageStub: PageStub): Page {
        val file = fileEntryRepository.getOrThrow(pageStub.pdfFileName)

        return Page(
            file,
            pageStub.title,
            pageStub.pagina,
            pageStub.type,
            pageStub.frameList,
            pageStub.dateDownload,
            pageStub.baseUrl
        )
    }

    suspend fun delete(page: Page) {
        getStub(page.pagePdf.name)?.let {
            appDatabase.pageDao().delete(it)
        }
        fileEntryRepository.delete(page.pagePdf.name)
    }

    suspend fun delete(pages: List<Page>) {
        appDatabase.pageDao().delete(
            pages.mapNotNull { getStub(it.pagePdf.name) }
        )

        fileEntryRepository.deleteList(pages.map { it.pagePdf.name })
    }

    suspend fun deleteIfNoIssueRelated(pages: List<Page>) {
        appDatabase.pageDao().deletePageFileEntriesIfNoIssueRelated(pages.map { it.pagePdf.name })
        appDatabase.pageDao().deleteIfNoIssueRelated(pages.map { it.pagePdf.name })
    }

    suspend fun getDownloadDate(page: Page): Date? {
        return appDatabase.pageDao().getDownloadDate(page.pagePdf.name)
    }

    suspend fun setDownloadDate(page: Page, date: Date?) {
        update(PageStub(page).copy(dateDownload = date))
    }
}