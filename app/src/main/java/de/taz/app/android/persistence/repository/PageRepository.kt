package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.annotation.UiThread
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.util.SingletonHolder

class PageRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<PageRepository, Context>(::PageRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    @UiThread
    fun save(page: Page) {
        appDatabase.runInTransaction {
            appDatabase.pageDao().insertOrReplace(
                PageStub(
                    page.pagePdf.name,
                    page.title,
                    page.pagina,
                    page.type,
                    page.frameList
                )
            )
            fileEntryRepository.save(page.pagePdf)
        }
    }

    @UiThread
    fun save(pages: List<Page>) {
        appDatabase.runInTransaction {
            pages.forEach { page ->
                save(page)
            }
        }
    }

    @UiThread
    fun getWithoutFile(fileName: String): PageStub? {
        return appDatabase.pageDao().get(fileName)
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getOrThrow(fileName: String): Page {
        val pageStub = appDatabase.pageDao().get(fileName)
        val file = fileEntryRepository.getOrThrow(fileName)

        return pageStub?.let {
            Page(
                file,
                pageStub.title,
                pageStub.pagina,
                pageStub.type,
                pageStub.frameList
            )
        } ?: throw NotFoundException()
    }

    @UiThread
    fun get(fileName: String): Page? {
        return try {
            getOrThrow(fileName)
        } catch (e: NotFoundException) {
            null
        }
    }

    @UiThread
    fun delete(page: Page) {
        appDatabase.runInTransaction {
            appDatabase.pageDao().delete(
                PageStub(
                    page.pagePdf.name,
                    page.title,
                    page.pagina,
                    page.type,
                    page.frameList
                )
            )
            fileEntryRepository.delete(page.pagePdf)
        }
    }

    @UiThread
    fun delete(pages: List<Page>) {
        pages.map { delete(it) }
    }
}