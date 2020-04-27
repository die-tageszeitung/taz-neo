package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.util.Log
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SectionRepositoryTest {

    private val log by Log

    private lateinit var db: AppDatabase
    private lateinit var sectionRepository: SectionRepository

    private val issue = IssueTestUtil.getIssue()
    private val sections = issue.sectionList
    private val section = sections.first()


    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        val fileEntryRepository = FileEntryRepository.createInstance(context)
        fileEntryRepository.appDatabase = db

        val articleRepository = ArticleRepository.createInstance(context)
        articleRepository.appDatabase = db

        val imageRepository = ImageRepository.createInstance(context)
        imageRepository.appDatabase = db

        sectionRepository = SectionRepository.getInstance(context)
        sectionRepository.appDatabase = db
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }


    @Test
    @Throws(Exception::class)
    fun writeAndRead() {
        sectionRepository.save(section)
        val fromDB = sectionRepository.get(section.sectionHtml.name)
        assertEquals(fromDB, section)
    }

    @Test
    @Throws(Exception::class)
    fun readBase() {
        sectionRepository.save(section)
        val fromDB = sectionRepository.getStub(section.sectionHtml.name)
        assertEquals(fromDB, SectionStub(section))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() {
        for (section in sections) {
            log.debug("checking section ${section.sectionHtml.name}")
            sectionRepository.save(section)
            val fromDB = sectionRepository.get(section.sectionHtml.name)
            assertEquals(fromDB, section)
        }
    }

}
