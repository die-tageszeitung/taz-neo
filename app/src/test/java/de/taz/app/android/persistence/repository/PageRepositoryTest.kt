package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.persistence.AppDatabase
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonsUtil
import de.taz.test.TestDataUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class PageRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var pageRepository: PageRepository

    private val issue = TestDataUtil.getIssue()
    private val pages = issue.pageList
    private val page = pages.first()


    @Before
    fun setUp() {
        SingletonsUtil.resetAll()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        val fileEntryRepository = FileEntryRepository.getInstance(context)
        fileEntryRepository.appDatabase = db

        val imageRepository = ImageRepository.getInstance(context)
        imageRepository.appDatabase = db

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
    fun writeAndRead() = runTest {
        pageRepository.saveInternal(page)
        val fromDB = pageRepository.get(page.pagePdf.name)
        assertEquals(fromDB, page)
    }

    @Test
    @Throws(Exception::class)
    fun getWithoutFile() = runTest {
        pageRepository.saveInternal(page)
        val fromDB = pageRepository.getWithoutFile(page.pagePdf.name)
        assertEquals(fromDB, PageStub(page))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() = runTest {
        for (page in pages) {
            assertTrue(pages.filter { it == page }.size == 1)

            pageRepository.saveInternal(page)
            val fromDB = pageRepository.get(page.pagePdf.name)
            assertEquals(fromDB, page)
        }
    }

}