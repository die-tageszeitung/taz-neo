package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.util.SingletonHolder

class PageRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<PageRepository, Context>(::PageRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

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

    fun save(pages: List<Page>) {
        appDatabase.runInTransaction {
            pages.forEach { page ->
                save(page)
            }
        }
    }

    fun getWithoutFile(fileName: String): PageStub? {
        return appDatabase.pageDao().get(fileName)
    }

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

    fun get(fileName: String): Page? {
        return try {
            getOrThrow(fileName)
        } catch (e: NotFoundException) {
            null
        }
    }

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

    fun delete(pages: List<Page>) {
        pages.map { delete(it) }
    }
}