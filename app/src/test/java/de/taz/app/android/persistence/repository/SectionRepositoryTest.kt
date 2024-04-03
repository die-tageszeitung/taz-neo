package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.util.Log
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import de.taz.test.TestDataUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class SectionRepositoryTest {

    private val log by Log

    private lateinit var db: AppDatabase
    private lateinit var sectionRepository: SectionRepository

    private val issue = TestDataUtil.getIssue()
    private val sections = issue.sectionList
    private val section = sections.first()


    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        val fileEntryRepository = FileEntryRepository.getInstance(context)
        fileEntryRepository.appDatabase = db

        val articleRepository = ArticleRepository.getInstance(context)
        articleRepository.appDatabase = db

        val imageRepository = ImageRepository.getInstance(context)
        imageRepository.appDatabase = db

        sectionRepository = SectionRepository.getInstance(context)
        sectionRepository.appDatabase = db

        TestDataUtil.createDefaultNavButton(imageRepository)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }


    @Test
    @Throws(Exception::class)
    fun writeAndRead() = runTest {
        sectionRepository.saveInternal(section)
        val fromDB = sectionRepository.get(section.sectionHtml.name)
        assertEquals(fromDB, section)
    }

    @Test
    @Throws(Exception::class)
    fun readBase()  = runTest {
        sectionRepository.saveInternal(section)
        val fromDB = sectionRepository.getStub(section.sectionHtml.name)
        assertEquals(fromDB, SectionStub(section))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() = runTest {
        for (section in sections) {
            log.debug("checking section ${section.sectionHtml.name}")
            sectionRepository.saveInternal(section)
            val fromDB = sectionRepository.get(section.sectionHtml.name)
            assertEquals(fromDB, section)
        }
    }

}
