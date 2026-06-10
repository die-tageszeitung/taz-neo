
package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import de.taz.test.TestDataUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class IssueRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var issueRepository: IssueRepository

    private val issue = TestDataUtil.getIssue()
    private val issue2 = TestDataUtil.getIssue("testIssue2")

    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        AppDatabase.inject(db)

        assertEquals(db, AppDatabase.getInstance(context))

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

        TestDataUtil.createDefaultNavButton(imageRepository)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun `saving and then retrieving an issue should return the same issue`() = runTest {
        // When
        issueRepository.save(issue)
        val retrievedIssue = issueRepository.get(issue.issueKey)

        // Then
        val expected = issue.withExpectedJoins()
        assertEquals(expected, retrievedIssue)
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

        val fromDB = issueRepository.getStub(issue.issueKey)
        val fromDB2 = issueRepository.getStub(issue2.issueKey)

        assertEquals(IssueStub(issue), fromDB)
        assertEquals(IssueStub(issue2), fromDB2)
    }

    @Test
    @Throws(Exception::class)
    fun getLatest() = runTest {
        val issueOld = issue.copy(date = "2020-01-01")
        val issueNew = issue.copy(date = "2020-01-02")

        issueRepository.save(issueOld)
        issueRepository.save(issueNew)

        assertEquals(IssueStub(issueNew), issueRepository.getLatestIssueStub())
    }


    @Test
    @Throws(Exception::class)
    fun getNextSection() = runTest {
        issueRepository.save(issue)
        issue.sectionList.forEachIndexed { index, section ->
            if (index == issue.sectionList.size - 1) {
                assertNull(section.next(context))
            } else {
                assertEquals(issue.sectionList[index + 1].key, section.next(context))
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
                assertEquals(issue.sectionList[index - 1].key, section.previous(context))
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
                        issue.sectionList[sectionIndex + 1].articleList.first().key
                    )
                } else {
                    assertEquals(
                        article.next(context),
                        section.articleList[articleIndex + 1].key
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
                        issue.sectionList[sectionIndex - 1].articleList.last().key
                    )
                } else {
                    assertEquals(
                        article.previous(context),
                        section.articleList[articleIndex - 1].key
                    )
                }
            }
        }
    }
    /**
     * Prepares an Issue fixture as it is expected to be returned from the repository.
     * This includes:
     * 1. Setting the [de.taz.app.android.persistence.join.SectionArticleJoin] for each article.
     * 2. Setting the [SectionStub] back-reference for each article.
     */
    private fun Issue.withExpectedJoins(): Issue {
        return this.copy(
            sectionList = sectionList.map { section ->
                val sectionStub = SectionStub(section)
                section.copy(
                    articleList = section.articleList.mapIndexed { index, article ->
                        article.copy(
                            sectionArticleJoin = SectionArticleJoin(section.key, article.key, index),
                            section = sectionStub
                        )
                    }
                )
            }
        )
    }
}