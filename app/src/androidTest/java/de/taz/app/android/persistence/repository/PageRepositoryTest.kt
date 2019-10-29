package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.persistence.AppDatabase
import org.junit.After
import org.junit.Before

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PageRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var pageRepository: PageRepository

    private val issue = IssueTestUtil.getIssue()
    private val pages = issue.pageList
    private val page = pages.first()


    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        val fileEntryRepository = FileEntryRepository.createInstance(context)
        fileEntryRepository.appDatabase = db

        pageRepository = PageRepository.getInstance(context)
        pageRepository.appDatabase = db
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }


    @Test
    @Throws(Exception::class)
    fun writeAndRead() {
        pageRepository.save(page)
        val fromDB = pageRepository.get(page.pagePdf.name)
        assertEquals(fromDB, page)
    }

    @Test
    @Throws(Exception::class)
    fun getWithoutFile() {
        pageRepository.save(page)
        val fromDB = pageRepository.getWithoutFile(page.pagePdf.name)
        assertEquals(fromDB, PageStub(page))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() {
        for (page in pages) {
            assertTrue(pages.filter { it == page }.size == 1)

            pageRepository.save(page)
            val fromDB = pageRepository.get(page.pagePdf.name)
            assertEquals(fromDB, page)
        }
    }

}