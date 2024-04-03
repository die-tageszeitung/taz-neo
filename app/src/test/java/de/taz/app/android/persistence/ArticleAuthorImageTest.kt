package de.taz.app.android.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.models.Image
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.test.Fixtures
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import de.taz.test.TestDataUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class ArticleAuthorImageTest {


    private lateinit var db: AppDatabase
    private lateinit var articleRepository: ArticleRepository
    private lateinit var sectionRepository: SectionRepository
    private lateinit var imageRepository: ImageRepository
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var defaultNavButton: Image

    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        AppDatabase.inject(db)

        articleRepository = ArticleRepository.getInstance(context)
        sectionRepository = SectionRepository.getInstance(context)
        imageRepository = ImageRepository.getInstance(context)
        fileEntryRepository = FileEntryRepository.getInstance(context)

        defaultNavButton = TestDataUtil.createDefaultNavButton(imageRepository)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    // Reproduces Bug https://redmine.hal.taz.de/issues/16312
    @Test
    fun `Article Image and FileEntry is kept even if a Section referencing it in its imageList is deleted`() = runTest {

        //
        // Given
        //
        val author = Fixtures.authorWithImage01
        val authorImage = Fixtures.authorImage01
        val authorImageFileName = authorImage.name

        val article = Fixtures.articleBase.copy(
            authorList = listOf(author)
        )

        val section = Fixtures.sectionBase.copy(
            navButton = defaultNavButton,
            imageList = listOf(authorImage)
        )

        //
        // prepare
        //
        articleRepository.saveInternal(article)
        assertEquals(article, articleRepository.get(article.key))

        // saving the Author trough the Article won't create an Image, only the FileEntry
        assertEquals(author.imageAuthor, fileEntryRepository.get(authorImageFileName))
        assertEquals(null, imageRepository.get(authorImageFileName))

        sectionRepository.saveInternal(section)
        assertEquals(section, sectionRepository.get(section.key))
        // saving the Author Image as part of the Section.imageList will create the Image in the db
        assertEquals(authorImage, imageRepository.get(authorImageFileName))

        //
        // when
        //
        sectionRepository.delete(section)

        //
        // then
        //
        assertEquals(authorImage, imageRepository.get(authorImageFileName))
        assertEquals(author.imageAuthor, fileEntryRepository.get(authorImageFileName))
        assertEquals(article, articleRepository.get(article.key))
    }
}