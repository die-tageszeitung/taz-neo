package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.AuthorJoinWithFile
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.api.models.ImageWithFile
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.test.Fixtures
import de.taz.test.Fixtures.copyWithFileName
import de.taz.test.Fixtures.image
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import de.taz.test.TestDataUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var sectionRepository: SectionRepository
    private lateinit var articleRepository: ArticleRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var imageRepository: ImageRepository

    private var testIssue: Issue = TestDataUtil.getIssue()

    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        context = ApplicationProvider.getApplicationContext<Context>()
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
    fun `saving and then retrieving aan article should return the same article`() = runTest {
        // Fix the fixture inconsistency before saving
        val article = Fixtures.article01.withExpectedIcon()

        // When
        articleRepository.saveInternal(article)
        val retrievedArticle = articleRepository.get(article.key)

        // Then
        assertEquals(article, retrievedArticle)
    }

    @Test
    @Throws(Exception::class)
    fun readBase() = runTest {
        val article = Fixtures.article01.withExpectedIcon()
        articleRepository.saveInternal(article)
        val fromDB = articleRepository.get(article.articleFileName)!!

        assertEquals(article.title, fromDB.title)
        assertEquals(article.teaser, fromDB.teaser)
        assertEquals(article.articleHtml, fromDB.articleHtml)
        assertEquals(article.imageList, fromDB.imageList)
        assertEquals(article.authorList, fromDB.authorList)
        assertEquals(article.audio, fromDB.audio)
        assertEquals(article.icon, fromDB.icon)
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() = runTest {
        val article = Fixtures.article01.withExpectedIcon()
        val article2 = Fixtures.article02.withExpectedIcon()

        articleRepository.saveInternal(article)
        articleRepository.saveInternal(article2)
        val fromDB = articleRepository.get(article.articleFileName)
        val fromDB2 = articleRepository.get(article2.articleFileName)

        assertEquals(article.articleStub, fromDB?.articleStub)
        assertEquals(article2.articleStub, fromDB2?.articleStub)
    }

    @Test
    @Throws(Exception::class)
    fun delete() = runTest {
        val article = Fixtures.article01.withExpectedIcon()
        articleRepository.saveInternal(article)
        val fromDB = articleRepository.get(article.articleFileName)
        assertEquals(fromDB, article)

        articleRepository.deleteArticle(article)
        assertNull(articleRepository.get(fromDB!!.articleFileName))
    }


    @Test
    @Throws(Exception::class)
    fun deleteBookmarkedFails() = runTest {
        val article = Fixtures.article01.withExpectedIcon()
        articleRepository.saveInternal(article)
        val fromDB = articleRepository.get(article.articleFileName)
        assertEquals(fromDB, article)

        bookmarkRepository.addBookmark(fromDB!!)
        val fromDBNew = articleRepository.get(article.articleFileName)
        articleRepository.deleteArticle(fromDBNew!!)
        assertNotNull(articleRepository.get(fromDBNew.articleFileName))
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
            authorJoins = listOf(AuthorJoinWithFile(Fixtures.articleBase.articleFileName, author, 0, 1))
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
        val articleFileName = "article01.html"
        val author = Fixtures.authorWithImage01
        val authorImageFileName = requireNotNull(author.imageAuthor).name
        val article01 = Fixtures.articleBase.copyWithFileName(articleFileName).copy(
            authorJoins = listOf(AuthorJoinWithFile(articleFileName, author, 0, 1))
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
            authorJoins = listOf(AuthorJoinWithFile(Fixtures.articleBase.articleFileName, author, 0, 1)),
            imagesWithFiles = listOf(authorImage).map { image -> ImageWithFile(ImageStub(image), Fixtures.fileEntry.copy(name = image.name)) }
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
            authorJoins = listOf(AuthorJoinWithFile(Fixtures.articleBase.articleFileName, author, 0, 1)),
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
                imagesWithFiles = listOf(image).map { image -> ImageWithFile(
                    ImageStub(image), Fixtures.fileEntry.copy(name = image.name, storageType = image.storageType)
                ) }
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
            imagesWithFiles = listOf(
                image,
                globalImage,
            ).map { image -> ImageWithFile(ImageStub(image), Fixtures.fileEntry.copy(name = image.name, storageType = image.storageType)) }
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

    @Test
    fun `ArticleStub and Article getAllFiles return always the same`() = runTest {
        testIssue.getArticles().forEach { article ->
            //
            // Prepare
            //
            articleRepository.saveInternal(article)

            //
            // WHEN
            //
            val stubFromDB = articleRepository.get(article.key)!!

            //
            // THEN
            //
            val fileList = article.getAllFiles(context)
            assertEquals(fileList, stubFromDB.getAllFiles(context))
            assertNotEquals(fileList, emptyList<FileEntry>())
        }
    }


    @Test
    fun `ArticleStub and Article getAuthorNames returns always the same`() = runTest {
        testIssue.getArticles().forEach { article ->
            //
            // Prepare
            //
            articleRepository.saveInternal(article)

            //
            // WHEN
            //
            val stubFromDB = articleRepository.get(article.key)!!

            //
            // THEN
            //
            val authorNames = article.getAuthorNames()
            assertEquals(authorNames, stubFromDB.getAuthorNames())
            assertNotEquals(authorNames, emptyList<FileEntry>())
        }
    }

    /**
     * Prepares an article fixture as it is expected to be returned from the repository.
     * This ensures that the iconFileName in the Stub matches the actual icon file.
     */
    private fun Article.withExpectedIcon(): Article {
        val fileName = iconWithFile?.imageStub?.fileEntryName
            ?: iconWithFile?.fileEntry?.name

        return this.copy(
            articleStub = this.articleStub.copy(
                iconFileName = fileName
            )
        )
    }
}
