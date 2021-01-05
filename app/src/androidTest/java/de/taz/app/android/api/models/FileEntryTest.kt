package de.taz.app.android.api.models

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.api.dto.FileEntryDto
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class FileEntryTest {

    private lateinit var storageService: StorageService
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var fileEntryRepository: FileEntryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java).build()
        fileEntryRepository = FileEntryRepository.getInstance(context)
        fileEntryRepository.appDatabase = db
        storageService = StorageService.createInstance(context)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun deleteFile() = runBlocking {
        val createdFileEntry = storageService.createOrUpdateFileEntry(fileEntryDtoTest, null)
        storageService.writeFile(createdFileEntry, ByteArrayInputStream(ByteArray(8)).toByteReadChannel())
        val createdFile = storageService.getFile(createdFileEntry)
        assertNotNull(
            fileEntryRepository.get(fileEntryDtoTest.name)
        )
        assertTrue(createdFile!!.exists())

        val fromDB = fileEntryRepository.get(createdFileEntry.name)

        assertNotNull(fromDB)
        storageService.deleteFile(fromDB!!)
        assertFalse(createdFile.exists())

        fileEntryRepository.delete(createdFileEntry)
        assertNull(
            fileEntryRepository.get(fileEntryDtoTest.name)
        )
    }
}

val fileEntryDtoTest = FileEntryDto("Ⓐ", StorageType.global, 1L, "sha256", 0)
