package de.taz.app.android.api.models

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import de.taz.test.TestDataUtil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class ArticleTest {

    private lateinit var db: AppDatabase
    private lateinit var articleRepository: ArticleRepository

    private val issue = TestDataUtil.getIssue()
    private val sections = issue.sectionList
    private val section = sections.first()
    private val article = section.articleList.first()

    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        val fileEntryRepository = FileEntryRepository.getInstance(context)
        fileEntryRepository.appDatabase = db

        articleRepository = ArticleRepository.getInstance(context)
        articleRepository.appDatabase = db
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getAllFiles() = runBlocking {
        articleRepository.saveInternal(article)
        val fileList = article.getAllFiles()

        assertTrue(fileList.filter { it == article.articleHtml }.size == 1)

        article.imageList
            .filter { it.name.contains(".norm") }
            .forEach { image ->
                assertTrue(fileList.filter { it.name == image.name }.size == 1)
            }
    }
}