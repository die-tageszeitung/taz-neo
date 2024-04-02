package de.taz.app.android.api.models

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonsUtil
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class FileEntryTest {

    private lateinit var db: AppDatabase
    private lateinit var storageService: StorageService
    private lateinit var fileEntryRepository: FileEntryRepository

    @Before
    fun setUp() {
        SingletonsUtil.resetAll()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        fileEntryRepository = FileEntryRepository.getInstance(context)
        fileEntryRepository.appDatabase = db
        storageService = StorageService.getInstance(context)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun deleteFile() = runBlocking {
        val fileEntryTest = createOrUpdateFileEntry("Ⓐ")
        val createdFileEntry =
            fileEntryRepository.saveOrReplace(fileEntryTest.copy(storageLocation = StorageLocation.INTERNAL))

        // determine storage location artificially
        storageService.writeFile(
            createdFileEntry,
            ByteArrayInputStream(ByteArray(8)).toByteReadChannel()
        )
        val createdFile = storageService.getFile(createdFileEntry)
        assertNotNull(
            fileEntryRepository.get(fileEntryTest.name)
        )
        assertTrue(createdFile!!.exists())

        val fromDB = fileEntryRepository.get(createdFileEntry.name)

        assertNotNull(fromDB)
        storageService.deleteFile(fromDB!!)
        assertFalse(createdFile.exists())

        fileEntryRepository.delete(createdFileEntry)
        assertNull(
            fileEntryRepository.get(fileEntryTest.name)
        )
    }

    private suspend fun createOrUpdateFileEntry(fileName: String): FileEntry {
        val path = StorageService.determineFilePath(StorageType.global, fileName, null)
        val existing = fileEntryRepository.get(fileName)
        val fileEntry = existing?.copy(
            path = path
        ) ?: FileEntry(
            fileName,
            StorageType.global,
            1L,
            "sha256",
            0,
            "",
            null,
            path,
            StorageLocation.INTERNAL
        )
        fileEntryRepository.saveOrReplace(fileEntry)
        return fileEntry
    }
}
