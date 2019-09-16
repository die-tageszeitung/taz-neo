package de.taz.app.android.persistence.repository

import org.junit.Assert.*

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.persistence.AppDatabase
import kotlinx.io.IOException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var articleRepository: ArticleRepository

    private val issue = IssueTestUtil.createIssue()
    private val article = issue.sectionList.first().articleList.first()
    private val article2 = issue.sectionList[1].articleList.first()

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
                context, AppDatabase::class.java).build()

        val fileEntryRepository = FileEntryRepository.createInstance(context)
        fileEntryRepository.appDatabase = db

        articleRepository = ArticleRepository.getInstance(context)
        articleRepository.appDatabase = db
        articleRepository
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeAndRead() {
        articleRepository.save(article)
        val fromDB = articleRepository.get(article.articleHtml.name)

        assertEquals(fromDB, article)
    }

    @Test
    @Throws(Exception::class)
    fun readBase() {
        articleRepository.save(article)
        val fromDB = articleRepository.getBase(article.articleHtml.name)

        assertEquals(fromDB, ArticleBase(article))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() {
        articleRepository.save(article)
        articleRepository.save(article2)
        val fromDB = articleRepository.get(article.articleHtml.name)
        val fromDB2 = articleRepository.get(article2.articleHtml.name)

        assertEquals(fromDB, article)
        assertEquals(fromDB2, article2)
    }

}
