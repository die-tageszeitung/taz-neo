package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.SingletonHolder
import java.util.*

class PageRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<PageRepository, Context>(::PageRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    fun update(pageStub: PageStub) {
        appDatabase.pageDao().update(pageStub)
    }

    fun save(page: Page) {
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
    }

    fun save(pages: List<Page>) {
        pages.forEach { page ->
            save(page)
        }
    }

    fun getWithoutFile(fileName: String): PageStub? {
        return appDatabase.pageDao().get(fileName)
    }

    fun getStub(fileName: String): PageStub? {
        return appDatabase.pageDao().get(fileName)
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(fileName: String): Page {
        appDatabase.pageDao().get(fileName)?.let {
            return pageStubToPage(it)
        } ?: throw NotFoundException()
    }

    fun get(fileName: String): Page? {
        return try {
            getOrThrow(fileName)
        } catch (e: NotFoundException) {
            null
        }
    }

    fun getFrontPage(issueKey: IssueKey): Page? {
        return appDatabase.issuePageJoinDao()
            .getFrontPageForIssue(issueKey.feedName, issueKey.date, issueKey.status)?.let {
                pageStubToPage(it)
            }
    }


    fun getLiveData(fileName: String): LiveData<Page?> {
        return Transformations.map(
            appDatabase.pageDao().getLiveData(fileName)
        ) { it?.let { pageStubToPage(it) } }
    }

    private fun pageStubToPage(pageStub: PageStub): Page {
        val file = fileEntryRepository.getOrThrow(pageStub.pdfFileName)

        return pageStub.let {
            Page(
                file,
                pageStub.title,
                pageStub.pagina,
                pageStub.type,
                pageStub.frameList,
                pageStub.dateDownload,
                pageStub.baseUrl
            )
        }
    }

    fun delete(page: Page) {
        getStub(page.pagePdf.name)?.let {
            appDatabase.pageDao().delete(it)
        }
        fileEntryRepository.delete(page.pagePdf.name)
    }

    fun delete(pages: List<Page>) {
        appDatabase.pageDao().delete(
            pages.mapNotNull { getStub(it.pagePdf.name) }
        )
        fileEntryRepository.deleteList(pages.map { it.pagePdf.name })
    }

    fun getDownloadDate(page: Page): Date? {
        return appDatabase.pageDao().getDownloadDate(page.pagePdf.name)
    }

    fun setDownloadDate(page: Page, date: Date?) {
        update(PageStub(page).copy(dateDownload = date))
    }

    fun isDownloadedLiveData(page: Page) = isDownloadedLiveData(page.pagePdf.name)

    fun isDownloadedLiveData(fileName: String): LiveData<Boolean> {
        return appDatabase.pageDao().isDownloadedLiveData(fileName)
    }
}