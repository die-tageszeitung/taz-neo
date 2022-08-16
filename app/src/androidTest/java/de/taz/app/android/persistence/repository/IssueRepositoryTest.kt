package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.TestDataUtil
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import java.io.IOException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class IssueRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var issueRepository: IssueRepository

    private val issue = TestDataUtil.getIssue()
    private val issue2 = TestDataUtil.getIssue("testIssue2")

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()

        val fileEntryRepository = FileEntryRepository.getInstance(context)
        fileEntryRepository.appDatabase = db
        val articleRepository = ArticleRepository.getInstance(context)
        articleRepository.appDatabase = db
        val pageRepository = PageRepository.getInstance(context)
        pageRepository.appDatabase = db
        val sectionRepository = SectionRepository.getInstance(context)
        sectionRepository.appDatabase = db
        val momentRepository = MomentRepository.getInstance(context)
        momentRepository.appDatabase = db
        val imageRepository = ImageRepository.getInstance(context)
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
    fun writeAndRead() = runTest {
        issueRepository.save(issue)
        val fromDB = issueRepository.getIssueByFeedDateAndStatus(
            issue.feedName, issue.date, issue.status
        )
        assertEquals(fromDB, issue)
    }

    @Test
    @Throws(Exception::class)
    fun readBase() = runTest {
        issueRepository.save(issue)
        val fromDB = issueRepository.getIssueStubByFeedDateAndStatus(
            issue.feedName, issue.date, issue.status
        )
        assertEquals(fromDB, IssueStub(issue))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() = runTest {
        issueRepository.save(issue)
        issueRepository.save(issue2)

        val fromDB = issueRepository.getIssueByFeedDateAndStatus(
            issue.feedName, issue.date, issue.status
        )
        val fromDB2 = issueRepository.getIssueByFeedDateAndStatus(
            issue2.feedName, issue2.date, issue2.status
        )

        assertEquals(fromDB, issue)
        assertEquals(fromDB2, issue2)
    }

    @Test
    @Throws(Exception::class)
    fun getLatest() = runTest {
        writeAndReadMultiple()
        assertEquals(IssueStub(issue), issueRepository.getLatestIssueStub())
    }


    @Test
    @Throws(Exception::class)
    fun getNextSection() = runTest {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { index, section ->
            if (index == issue.sectionList.size - 1) {
                assertNull(section.next(context))
            } else {
                assertEquals(issue.sectionList[index + 1].sectionHtml.sha256, section.next(context)?.sectionHtml?.sha256)
            }

        }
    }

    @Test
    @Throws(Exception::class)
    fun getPreviousSection() = runTest {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { index, section ->
            if (index == 0) {
                assertNull(section.previous(context))
            } else {
                assertEquals(issue.sectionList[index - 1].sectionHtml.sha256, section.previous(context)?.sectionHtml?.sha256)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun getNextArticle() = runTest {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { sectionIndex, section ->
            section.articleList.forEachIndexed { articleIndex, article ->
                if (sectionIndex == issue.sectionList.size - 1 &&
                    articleIndex == section.articleList.size - 1
                ) {
                    assertNull(article.next(context))
                } else if (articleIndex == section.articleList.size - 1) {
                    assertEquals(
                        article.next(context),
                        issue.sectionList[sectionIndex + 1].articleList.first()
                    )
                } else {
                    assertEquals(
                        article.next(context),
                        section.articleList[articleIndex + 1]
                    )
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun getPreviousArticle() = runTest {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { sectionIndex, section ->
            section.articleList.forEachIndexed { articleIndex, article ->
                if (sectionIndex == 0 && articleIndex == 0) {
                    assertNull(article.previous(context))
                } else if (articleIndex == 0) {
                    assertEquals(
                        article.previous(context),
                        issue.sectionList[sectionIndex - 1].articleList.last()
                    )
                } else {
                    assertEquals(
                        article.previous(context),
                        section.articleList[articleIndex - 1]
                    )
                }
            }
        }
    }

}