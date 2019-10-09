package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.api.models.IssueBase
import de.taz.app.android.api.models.Moment
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

    private val issue = IssueTestUtil.getIssue()
    private val issue2 = IssueTestUtil.getIssue("testIssue2")

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
        val momentRepository = MomentRepository.createInstance(context)
        momentRepository.appDatabase = db

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
        issueRepository.save(issue)
        issueRepository.save(issue2)

        val fromDB = issueRepository.getIssueByFeedAndDate(issue.feedName, issue.date)
        val fromDB2 = issueRepository.getIssueByFeedAndDate(issue2.feedName, issue2.date)

        assertEquals(fromDB, issue)
        assertEquals(fromDB2, issue2)
    }

    @Test
    @Throws(Exception::class)
    fun getLatest() {
        writeAndReadMultiple()
        assertTrue(issueRepository.getLatestIssue() == issue2)
    }


    @Test
    @Throws(Exception::class)
    fun getNextSection() {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { index, section->
            if (index == issue.sectionList.size -1) {
                assertNull(section.nextSection())
            } else {
                assertEquals(issue.sectionList[index +1], section.nextSection())
            }

        }
    }

    @Test
    @Throws(Exception::class)
    fun getPreviousSection() {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { index, section->
            if (index == 0) {
                assertNull(section.previousSection())
            } else {
                assertEquals(issue.sectionList[index -1], section.previousSection())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun getNextArticle() {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { sectionIndex, section ->
            section.articleList.forEachIndexed { articleIndex, article ->
                if(sectionIndex == issue.sectionList.size - 1 && articleIndex == section.articleList.size - 1 ) {
                    assertNull(article.nextArticle())
                } else if(articleIndex == section.articleList.size - 1) {
                    assertEquals(article.nextArticle(), issue.sectionList[sectionIndex + 1].articleList.first())
                } else {
                    assertEquals(article.nextArticle(), section.articleList[articleIndex + 1])
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun getPreviousArticle() {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { sectionIndex, section ->
            section.articleList.forEachIndexed { articleIndex, article ->
                if(sectionIndex == 0 && articleIndex == 0 ) {
                    assertNull(article.previousArticle())
                } else if(articleIndex == 0) {
                    assertEquals(article.previousArticle(), issue.sectionList[sectionIndex - 1].articleList.last())
                } else {
                    assertEquals(article.previousArticle(), section.articleList[articleIndex - 1])
                }
            }
        }
    }

}