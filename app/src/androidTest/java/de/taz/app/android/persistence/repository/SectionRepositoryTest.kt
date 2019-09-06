package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.api.models.SectionBase
import de.taz.app.android.persistence.AppDatabase
import org.junit.After
import org.junit.Before

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SectionRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var sectionRepository: SectionRepository

    private val issue = IssueTestUtil.createIssue()
    private val sections = issue.sectionList
    private val section = sections.first()


    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        sectionRepository = SectionRepository(db)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }


    @Test
    @Throws(Exception::class)
    fun writeAndRead() {
        val prefromDB = sectionRepository.get(section.sectionHtml.name)
        sectionRepository.save(section)
        val fromDB = sectionRepository.get(section.sectionHtml.name)
        assertEquals(fromDB, section)
    }

    @Test
    @Throws(Exception::class)
    fun readBase() {
        sectionRepository.save(section)
        val fromDB = sectionRepository.getBase(section.sectionHtml.name)
        assertEquals(fromDB, SectionBase(section))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() {
        for (section in sections) {
            assertTrue(sections.filter { it == section }.size == 1)

            sectionRepository.save(section)
            val fromDB = sectionRepository.get(section.sectionHtml.name)
            assertEquals(fromDB, section)
        }
    }

}
