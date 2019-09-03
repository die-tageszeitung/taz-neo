package de.taz.app.android.persistence.repository

import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageWithoutFile
import de.taz.app.android.persistence.AppDatabase

class PageRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance()) {

    fun save(page: Page) {
        appDatabase.pageDao().insertOrReplace(
            PageWithoutFile(page.pagePdf.name, page.title, page.pagina, page.type, page.frameList)
        )
        appDatabase.fileEntryDao().insertOrAbort(page.pagePdf)
    }

    fun save(pages: List<Page>) {
        pages.forEach { page ->
            save(page)
        }
    }

    fun getWithoutFile(fileName: String): PageWithoutFile {
        return appDatabase.pageDao().get(fileName)
    }

    fun get(fileName: String): Page {
        val pageWithoutFile = appDatabase.pageDao().get(fileName)
        val file = appDatabase.fileEntryDao().getByName(fileName)

        return Page(
            file,
            pageWithoutFile.title,
            pageWithoutFile.pagina,
            pageWithoutFile.type,
            pageWithoutFile.frameList
        )
    }
}