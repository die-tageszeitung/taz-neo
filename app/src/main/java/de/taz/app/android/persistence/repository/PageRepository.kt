package de.taz.app.android.persistence.repository

import androidx.room.Transaction
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageWithoutFile
import de.taz.app.android.persistence.AppDatabase

class PageRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance()) {

    private val fileEntryRepository = FileEntryRepository(appDatabase)

    fun save(page: Page) {
        appDatabase.pageDao().insertOrReplace(
            PageWithoutFile(page.pagePdf.name, page.title, page.pagina, page.type, page.frameList)
        )
        fileEntryRepository.save(page.pagePdf)
    }

    @Transaction
    fun save(pages: List<Page>) {
        pages.forEach { page ->
            save(page)
        }
    }

    fun getWithoutFile(fileName: String): PageWithoutFile {
        return appDatabase.pageDao().get(fileName)
    }

    fun getOrThrow(fileName: String): Page {
        val pageWithoutFile = appDatabase.pageDao().get(fileName)
        val file = fileEntryRepository.getOrThrow(fileName)

        return try {
            Page(
                file,
                pageWithoutFile.title,
                pageWithoutFile.pagina,
                pageWithoutFile.type,
                pageWithoutFile.frameList
            )
        } catch (e: Exception) {
            throw NotFoundException()
        }
    }

    fun get(fileName: String): Page? {
        return try {
            getOrThrow(fileName)
        } catch (e: Exception) {
            null
        }
    }
}