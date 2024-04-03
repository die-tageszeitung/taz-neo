package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.test.Fixtures
import de.taz.test.Fixtures.copyWithFileName
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
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

    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
                context, AppDatabase::class.java).build()
        AppDatabase.inject(db)

        articleRepository = ArticleRepository.getInstance(context)
        sectionRepository = SectionRepository.getInstance(context)
        bookmarkRepository = BookmarkRepository.getInstance(context)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeAndRead() = runTest {
        val article = Fixtures.article01
        articleRepository.saveInternal(article)
        val fromDB = articleRepository.get(article.articleHtml.name)

        assertEquals(fromDB, article)
    }

    @Test
    @Throws(Exception::class)
    fun readBase() = runTest {
        val article = Fixtures.article01
        articleRepository.saveInternal(article)
        val fromDB = articleRepository.getStub(article.articleHtml.name)

        assertEquals(fromDB, ArticleStub(article))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() = runTest {
        val article = Fixtures.article01
        val article2 = Fixtures.article02

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
        val article = Fixtures.article01
        articleRepository.saveInternal(article)
        val fromDB = articleRepository.get(article.articleHtml.name)
        assertEquals(fromDB, article)

        articleRepository.deleteArticle(article)
        assertNull(articleRepository.get(fromDB!!.articleHtml.name))
    }


    @Test
    @Throws(Exception::class)
    fun deleteBookmarkedFails() = runTest {
        val article = Fixtures.article01
        articleRepository.saveInternal(article)
        val fromDB = articleRepository.get(article.articleHtml.name)
        assertEquals(fromDB, article)

        bookmarkRepository.addBookmark(fromDB!!)
        val fromDBNew = articleRepository.get(article.articleHtml.name)
        articleRepository.deleteArticle(fromDBNew!!)
        assertNotNull(articleRepository.get(fromDBNew.articleHtml.name))
    }

    @Test
    fun `When Article is deleted then no longer referenced Authors are deleted`() = runTest {
        //
        // Given
        //
        val author = Fixtures.authorWithImage01
        val authorImageFileName = requireNotNull(author.imageAuthor).name
        val article = Fixtures.articleBase.copy(
            authorList = listOf(author),
        )

        articleRepository.saveInternal(article)

        val authorJoins = db.articleAuthorImageJoinDao().getArticlesForAuthor(authorImageFileName)
        assertEquals(1, authorJoins.size)
        assertEquals(article.key, authorJoins[0].articleFileName)


        //
        // WHEN
        //
        articleRepository.deleteArticle(article)


        //
        // THEN
        //
        assertEquals(emptyList<ArticleAuthorImageJoin>(), db.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(article.key))
        assertEquals(emptyList<ArticleAuthorImageJoin>(), db.articleAuthorImageJoinDao().getArticlesForAuthor(authorImageFileName))
    }

    @Test
    fun `When Article is deleted then still referenced Authors are kept`() = runTest {
        //
        // Given
        //
        val author = Fixtures.authorWithImage01
        val authorImageFileName = requireNotNull(author.imageAuthor).name
        val article01 = Fixtures.articleBase.copyWithFileName("article01.html").copy(
            authorList = listOf(author),
        )
        val article02 = article01.copyWithFileName("article02.html")


        // Save both articles sharing the same author
        articleRepository.saveInternal(article01)
        articleRepository.saveInternal(article02)

        assertEquals(2, db.articleAuthorImageJoinDao().getArticlesForAuthor(authorImageFileName).size)

        //
        // WHEN
        //
        articleRepository.deleteArticle(article01)


        //
        // THEN
        //
        assertEquals(emptyList<ArticleAuthorImageJoin>(), db.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(article01.key))
        assertEquals(1, db.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(article02.key).size)
        assertEquals(1, db.articleAuthorImageJoinDao().getArticlesForAuthor(authorImageFileName).size)
    }
}
