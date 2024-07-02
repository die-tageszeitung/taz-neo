package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.util.Log
import de.taz.test.Fixtures
import de.taz.test.Fixtures.copyWithFileName
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import de.taz.test.TestDataUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class SectionRepositoryTest {

    private val log by Log

    private lateinit var db: AppDatabase
    private lateinit var sectionRepository: SectionRepository
    private lateinit var imageRepository: ImageRepository
    private lateinit var fileEntryRepository: FileEntryRepository

    private val issue = TestDataUtil.getIssue()
    private val sections = issue.sectionList
    private val section = sections.first()


    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        AppDatabase.inject(db)

        sectionRepository = SectionRepository.getInstance(context)
        imageRepository = ImageRepository.getInstance(context)
        fileEntryRepository = FileEntryRepository.getInstance(context)

        TestDataUtil.createDefaultNavButton(imageRepository)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }


    @Test
    @Throws(Exception::class)
    fun writeAndRead() = runTest {
        sectionRepository.saveInternal(section)
        val fromDB = sectionRepository.get(section.sectionHtml.name)
        assertEquals(fromDB, section)
    }

    @Test
    @Throws(Exception::class)
    fun readBase()  = runTest {
        sectionRepository.saveInternal(section)
        val fromDB = sectionRepository.getStub(section.sectionHtml.name)
        assertEquals(fromDB, SectionStub(section))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() = runTest {
        for (section in sections) {
            log.debug("checking section ${section.sectionHtml.name}")
            sectionRepository.saveInternal(section)
            val fromDB = sectionRepository.get(section.sectionHtml.name)
            assertEquals(fromDB, section)
        }
    }


    @Test
    fun `When Section is deleted, referenced images of StorageType global, are kept`() = runTest {
        //
        // Given
        //
        val image = Fixtures.image
        val globalImage = Fixtures.image.copy(name="global.png", storageType = StorageType.global)
        val section = Fixtures.sectionBase.copy(
            imageList = listOf(
                image,
                globalImage,
            )
        )

        //
        // Prepare
        //
        sectionRepository.saveInternal(section)
        assertNotNull(fileEntryRepository.get(globalImage.name))
        assertNotNull(imageRepository.get(image.name))

        //
        // WHEN
        //
        sectionRepository.delete(section)

        //
        // THEN
        //
        assertNotNull(fileEntryRepository.get(globalImage.name))
        assertNull(imageRepository.get(image.name))
    }

    @Test
    fun `Section can be deleted, if it contains an Image referenced from another Section`() = runTest {
        // This case might occur for example when a user logs in and the regular Issues are downloaded
        // in addition to the public ones. The public ones reference the same Images.

        //
        // Given
        //
        val image = Fixtures.image.copy(storageType = StorageType.issue)
        val publicSection = Fixtures.sectionBase.copyWithFileName("public.html").copy(
            imageList = listOf(image),
        )
        val regularSection = Fixtures.sectionBase.copyWithFileName("regular.html").copy(
            imageList = listOf(image),
        )

        //
        // Prepare
        //
        sectionRepository.saveInternal(publicSection)
        sectionRepository.saveInternal(regularSection)

        //
        // WHEN
        //
        sectionRepository.delete(publicSection)

        //
        // THEN
        //
        assertNull(sectionRepository.get(publicSection.key))
    }
}
