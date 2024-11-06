package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ImageResolution
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.api.models.ImageType
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.persistence.AppDatabase
import de.taz.test.Fixtures
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class ImageRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var imageRepository: ImageRepository
    private lateinit var fileEntryRepository: FileEntryRepository

    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        AppDatabase.inject(db)

        imageRepository = ImageRepository.getInstance(context)
        fileEntryRepository = FileEntryRepository.getInstance(context)

    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `ImageStub without a referenced FileEntry can be deleted`() = runTest {
        val image = Image(
            "image.png",
            StorageType.global,
            0L,
            "",
            0L,
            "",
            ImageType.picture,
            0f,
            ImageResolution.normal,
            null,
            StorageLocation.NOT_STORED
        )
        val imageStub = ImageStub(image)

        imageRepository.saveInternal(image)

        // Delete the FileEntry of the Image with FK constraint checks disabled
        // This could happen in versions including 1.9.0
        db.openHelper.writableDatabase.apply {
            execSQL("PRAGMA foreign_keys = OFF")
            delete("FileEntry", null, null)
            execSQL("PRAGMA foreign_keys = ON")
        }

        assertNull(fileEntryRepository.get(image.name))
        assertEquals(imageStub, imageRepository.getStub(image.name))

        imageRepository.delete(image)
        assertNull(imageRepository.getStub(image.name))
    }

    // This deletion behavior is required when trying to delete AuthorImage FileEntrys from the ArticleRepository.delete
    // FIXME (johannes): we might drop this if we refactor the AuthorImage delete and use a scrubber for global
    @Test
    fun `Image related FileEntry is deleted, even if no ImageStub exists`() = runTest {
        val fileEntry = FileEntry(
            "image.png",
            StorageType.global,
            0L,
            "",
            0L,
            null,
            "",
            StorageLocation.NOT_STORED
        )
        fileEntryRepository.save(fileEntry)
        assertEquals(fileEntry, fileEntryRepository.get(fileEntry.name))

        imageRepository.delete(fileEntry)
        assertNull(fileEntryRepository.get(fileEntry.name))
    }

    @Test
    fun `Image Entry can be deleted`() = runTest {
        //
        // GIVEN
        //
        val image = Fixtures.image
        imageRepository.saveInternal(image)

        //
        // WHEN, THEN
        //
        imageRepository.delete(image)
    }

    @Test
    fun `Image Entry can be deleted when passed as a FileEntry`() = runTest {
        //
        // GIVEN
        //
        val image = Fixtures.image
        imageRepository.saveInternal(image)

        //
        // WHEN, THEN
        //
        val imageFileEntry = FileEntry(image)
        imageRepository.delete(imageFileEntry)
    }
}