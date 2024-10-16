package de.taz.app.android.api.models

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.SectionRepository
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class SectionTest {

    private lateinit var applicationContext: Context
    private lateinit var db: AppDatabase
    private lateinit var sectionRepository: SectionRepository

    private val issue = TestDataUtil.getIssue()
    private val sections = issue.sectionList
    private val section = sections.first()


    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        applicationContext = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            applicationContext, AppDatabase::class.java
        ).build()
        val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        fileEntryRepository.appDatabase = db

        val articleRepository = ArticleRepository.getInstance(applicationContext)
        articleRepository.appDatabase = db

        val imageRepository = ImageRepository.getInstance(applicationContext)
        imageRepository.appDatabase = db

        sectionRepository = SectionRepository.getInstance(applicationContext)
        sectionRepository.appDatabase = db
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun getAllFiles() = runBlocking {
        sectionRepository.saveInternal(section)
        val fileList = section.getAllFiles(applicationContext)

        assertTrue(fileList.filter { it == section.sectionHtml }.size == 1)
        assertTrue(fileList.none { it.name.startsWith("art") && it.name.endsWith(".html") })

        section.imageList
            .filter { it.resolution == ImageResolution.normal }
            .forEach { image ->
                assertTrue(fileList.filter { it.name == image.name }.size == 1)
            }
    }
}