package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageWithoutFile
import de.taz.app.android.util.SingletonHolder

class PageRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<PageRepository, Context>(::PageRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    fun save(page: Page) {
        appDatabase.runInTransaction {
            appDatabase.pageDao().insertOrReplace(
                PageWithoutFile(
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

    fun getWithoutFile(fileName: String): PageWithoutFile? {
        return appDatabase.pageDao().get(fileName)
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(fileName: String): Page {
        val pageWithoutFile = appDatabase.pageDao().get(fileName)
        val file = fileEntryRepository.getOrThrow(fileName)

        return pageWithoutFile?.let {
            Page(
                file,
                pageWithoutFile.title,
                pageWithoutFile.pagina,
                pageWithoutFile.type,
                pageWithoutFile.frameList
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
                PageWithoutFile(
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

    fun delete(pages: List<Page>)  {
        pages.map { delete(it) }
    }
}