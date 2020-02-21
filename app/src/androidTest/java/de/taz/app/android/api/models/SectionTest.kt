package de.taz.app.android.api.models

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.SectionRepository
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException

class SectionTest {

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

        sectionRepository = SectionRepository.getInstance(context)
        sectionRepository.appDatabase = db
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun getAllFiles() {
        sectionRepository.save(section)
        val fileList = section.getAllFiles()

        assertTrue(fileList.filter { it == section.sectionHtml }.size == 1)
        assertTrue(fileList.none { it.name.startsWith("art") && it.name.endsWith(".html") })

        section.imageList.forEach { fileEntry ->
            assertTrue(fileList.filter { it == fileEntry }.size == 1)
        }
    }
}