package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.StorageType
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
import org.junit.Ignore
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
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var imageRepository: ImageRepository

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
        fileEntryRepository = FileEntryRepository.getInstance(context)
        imageRepository = ImageRepository.getInstance(context)
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
    fun `When Article is deleted Author FileEntries are kept`() = runTest {
        //
        // Given
        //
        val author = Fixtures.authorWithImage01
        val authorImage = Fixtures.authorImage01
        val authorImageFile = requireNotNull(author.imageAuthor)
        assertEquals(authorImage.name, authorImageFile.name)
        val article = Fixtures.articleBase.copy(
            authorList = listOf(author),
        )

        articleRepository.saveInternal(article)

        //
        // WHEN
        //
        articleRepository.deleteArticle(article)

        //
        // THEN
        //
        assertNotNull(fileEntryRepository.get(authorImageFile.name))
    }

    @Ignore("This case is no longer needed as long as all Author Images are kept. See other test")
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


    @Ignore("This case is no longer needed as long as all Author Images are kept. See other test")
    @Test
    fun `When Author Image is referenced additionally from Article Image List, the Article can be deleted`() = runTest {
        //
        // Given
        //
        val author = Fixtures.authorWithImage01
        val authorImage = Fixtures.authorImage01
        val authorImageFile = requireNotNull(author.imageAuthor)
        assertEquals(authorImage.name, authorImageFile.name)
        val article = Fixtures.articleBase.copy(
            authorList = listOf(author),
            imageList = listOf(authorImage)
        )

        articleRepository.saveInternal(article)

        //
        // WHEN
        //
        articleRepository.deleteArticle(article)

        //
        // THEN
        //
        assertNull(articleRepository.get(article.key))
    }

    @Test
    fun `When Author Image is referenced additionally from Section Image List, the Article can be deleted`() = runTest {
        //
        // Given
        //
        val author = Fixtures.authorWithImage01
        val authorImage = Fixtures.authorImage01
        val authorImageFile = requireNotNull(author.imageAuthor)
        assertEquals(authorImage.name, authorImageFile.name)
        val article = Fixtures.articleBase.copy(
            authorList = listOf(author),
        )

        val section = Fixtures.sectionBase.copy(
            imageList = listOf(authorImage),
        )

        sectionRepository.saveInternal(section)
        articleRepository.saveInternal(article)

        //
        // WHEN
        //
        articleRepository.deleteArticle(article)


        //
        // THEN
        //
        assertNull(articleRepository.get(article.key))
    }

    @Test
    fun `When Article Images are referenced additionally from another Article, the Article can be deleted`() = runTest {
        // This case might occur for example when a user logs in and the regular Issues are downloaded
        // in addition to the public ones. The public ones reference the same Images.

        //
        // Given
        //
        val image = Fixtures.image
        val publicArticle = Fixtures.articleBase
            .copyWithFileName("public.html")
            .copy(
                imageList = listOf(image)
            )
        val regularArticle = publicArticle.copyWithFileName("regular.html")

        articleRepository.saveInternal(publicArticle)
        articleRepository.saveInternal(regularArticle)

        //
        // WHEN
        //
        articleRepository.deleteArticle(publicArticle)

        //
        // THEN
        //
        assertNull(articleRepository.get(publicArticle.key))
        assertNotNull(articleRepository.get(regularArticle.key))
    }

    @Test
    fun `When Article is deleted global Images are kept`() = runTest {
        //
        // Given
        //
        val image = Fixtures.image
        val globalImage = Fixtures.image.copy(name="global.png", storageType = StorageType.global)
        val article = Fixtures.articleBase.copy(
            imageList = listOf(
                image,
                globalImage,
            )
        )

        //
        // Prepare
        //
        articleRepository.saveInternal(article)
        assertNotNull(fileEntryRepository.get(globalImage.name))
        assertNotNull(imageRepository.get(image.name))

        //
        // WHEN
        //
        articleRepository.deleteArticle(article)

        //
        // THEN
        //
        assertNotNull(fileEntryRepository.get(globalImage.name))
        assertNull(imageRepository.get(image.name))
    }
}
