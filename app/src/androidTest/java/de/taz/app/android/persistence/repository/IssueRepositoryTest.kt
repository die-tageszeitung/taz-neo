package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.api.models.File
import de.taz.app.android.api.models.IssueBase
import de.taz.app.android.persistence.AppDatabase
import kotlinx.io.IOException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssueRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var issueRepository: IssueRepository

    private val issues = IssueTestUtil.createIssue(3)
    private val issue = issues.first()

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()

        val fileEntryRepository = FileEntryRepository.createInstance(context)
        fileEntryRepository.appDatabase = db
        val articleRepository = ArticleRepository.createInstance(context)
        articleRepository.appDatabase = db
        val pageRepository = PageRepository.createInstance(context)
        pageRepository.appDatabase = db
        val sectionRepository = SectionRepository.createInstance(context)
        sectionRepository.appDatabase = db

        issueRepository = IssueRepository.getInstance(context)
        issueRepository.appDatabase = db
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeAndRead() {
        issueRepository.save(issue)
        val fromDB = issueRepository.getIssueByFeedAndDate(issue.feedName, issue.date)
        assertEquals(fromDB, issue)
    }

    @Test
    @Throws(Exception::class)
    fun readBase() {
        issueRepository.save(issue)
        val fromDB = issueRepository.getIssueBaseByFeedAndDate(issue.feedName, issue.date)
        assertEquals(fromDB, IssueBase(issue))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() {
        for (issue in issues) {
            assertTrue(issues.filter { it == issue }.size == 1)

            issueRepository.save(issue)
            val fromDB = issueRepository.getIssueByFeedAndDate(issue.feedName, issue.date)

            assertEquals(fromDB, issue)
        }
    }

    @Test
    @Throws(Exception::class)
    fun getLatest() {
        writeAndReadMultiple()
        val latestIssue = issues.last()
        assertTrue(issueRepository.getLatestIssue() == latestIssue)
    }


}