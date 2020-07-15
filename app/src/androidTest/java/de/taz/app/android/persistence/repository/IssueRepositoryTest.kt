package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
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
        val imageRepository = ImageRepository.createInstance(context)
        imageRepository.appDatabase = db

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
        val fromDB = issueRepository.getIssueByFeedAndDate(
            issue.feedName, issue.date, issue.status
        )
        assertEquals(fromDB, issue)
    }

    @Test
    @Throws(Exception::class)
    fun readBase() {
        issueRepository.save(issue)
        val fromDB = issueRepository.getIssueStubByFeedAndDate(
            issue.feedName, issue.date, issue.status
        )
        assertEquals(fromDB, IssueStub(issue))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() {
        issueRepository.save(issue)
        issueRepository.save(issue2)

        val fromDB = issueRepository.getIssueByFeedAndDate(
            issue.feedName, issue.date, issue.status
        )
        val fromDB2 = issueRepository.getIssueByFeedAndDate(
            issue2.feedName, issue2.date, issue.status
        )

        assertEquals(fromDB, issue)
        assertEquals(fromDB2, issue2)
    }

    @Test
    @Throws(Exception::class)
    fun getLatest() {
        writeAndReadMultiple()
        assertEquals(issue, issueRepository.getLatestIssue())
    }


    @Test
    @Throws(Exception::class)
    fun getNextSection() {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { index, section ->
            if (index == issue.sectionList.size - 1) {
                assertNull(section.next())
            } else {
                assertEquals(SectionStub(issue.sectionList[index + 1]), section.next())
            }

        }
    }

    @Test
    @Throws(Exception::class)
    fun getPreviousSection() {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { index, section ->
            if (index == 0) {
                assertNull(section.previous())
            } else {
                assertEquals(SectionStub(issue.sectionList[index - 1]), section.previous())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun getNextArticle() {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { sectionIndex, section ->
            section.articleList.forEachIndexed { articleIndex, article ->
                if (sectionIndex == issue.sectionList.size - 1 &&
                    articleIndex == section.articleList.size - 1
                ) {
                    assertNull(article.next())
                } else if (articleIndex == section.articleList.size - 1) {
                    assertEquals(
                        article.next(),
                        ArticleStub(issue.sectionList[sectionIndex + 1].articleList.first())
                    )
                } else {
                    assertEquals(
                        article.next(),
                        ArticleStub(section.articleList[articleIndex + 1])
                    )
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
                if (sectionIndex == 0 && articleIndex == 0) {
                    assertNull(article.previous())
                } else if (articleIndex == 0) {
                    assertEquals(
                        article.previous(),
                        ArticleStub(issue.sectionList[sectionIndex - 1].articleList.last())
                    )
                } else {
                    assertEquals(
                        article.previous(),
                        ArticleStub(section.articleList[articleIndex - 1])
                    )
                }
            }
        }
    }

}