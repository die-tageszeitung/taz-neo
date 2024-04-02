package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.persistence.AppDatabase
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonsUtil
import de.taz.test.TestDataUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class ArticleRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var sectionRepository: SectionRepository
    private lateinit var articleRepository: ArticleRepository
    private lateinit var bookmarkRepository: BookmarkRepository

    private val issue = TestDataUtil.getIssue()
    private val article = issue.sectionList.first().articleList.first()
    private val article2 = issue.sectionList[1].articleList.first()

    @Before
    fun setUp() {
        SingletonsUtil.resetAll()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
                context, AppDatabase::class.java).build()

        val fileEntryRepository = FileEntryRepository.getInstance(context)
        fileEntryRepository.appDatabase = db

        articleRepository = ArticleRepository.getInstance(context)
        articleRepository.appDatabase = db

        sectionRepository = SectionRepository.getInstance(context)
        sectionRepository.appDatabase = db

        bookmarkRepository = BookmarkRepository.getInstance(context)
        bookmarkRepository.appDatabase = db
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeAndRead() = runTest {
        articleRepository.saveInternal(article)
        val fromDB = articleRepository.get(article.articleHtml.name)

        assertEquals(fromDB, article)
    }

    @Test
    @Throws(Exception::class)
    fun readBase() = runTest {
        articleRepository.saveInternal(article)
        val fromDB = articleRepository.getStub(article.articleHtml.name)

        assertEquals(fromDB, ArticleStub(article))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() = runTest {
        articleRepository.saveInternal(article)
        articleRepository.saveInternal(article2)
        val fromDB = articleRepository.get(article.articleHtml.name)
        val fromDB2 = articleRepository.get(article2.articleHtml.name)

        assertEquals(fromDB, article)
        assertEquals(fromDB2, article2)
    }

    @Test
    @Throws(Exception::class)
    fun delete() = runTest {
        articleRepository.saveInternal(article)
        val fromDB = articleRepository.get(article.articleHtml.name)
        assertEquals(fromDB, article)

        articleRepository.deleteArticle(article)
        assertNull(articleRepository.get(fromDB!!.articleHtml.name))
    }


    @Test
    @Throws(Exception::class)
    fun deleteBookmarkedFails() = runTest {
        articleRepository.saveInternal(article)
        val fromDB = articleRepository.get(article.articleHtml.name)
        assertEquals(fromDB, article)

        bookmarkRepository.addBookmark(fromDB!!)
        val fromDBNew = articleRepository.get(article.articleHtml.name)
        articleRepository.deleteArticle(fromDBNew!!)
        assertNotNull(articleRepository.get(fromDBNew.articleHtml.name))
    }

}
