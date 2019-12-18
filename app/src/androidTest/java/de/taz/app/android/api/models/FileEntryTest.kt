package de.taz.app.android.api.models

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.api.interfaces.StorageType
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.FileHelper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class FileEntryTest {

    private lateinit var fileHelper: FileHelper
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var downloadRepository: DownloadRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java).build()
        fileEntryRepository = FileEntryRepository.getInstance(context)
        fileEntryRepository.appDatabase = db
        downloadRepository = DownloadRepository.getInstance(context)
        downloadRepository.appDatabase = db
        fileHelper = FileHelper.createInstance(context)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun deleteFile() {
        fileEntryRepository.save(fileEntryTest)
        val fromDB = fileEntryRepository.get(fileEntryTest.name)
        assertEquals(fileEntryTest, fromDB)

        val download = Download(
            "https://example.com",
            fileEntryTest
        )
        downloadRepository.save(download)

        fileHelper.createFile(fileEntryTest)
        val createdFile = fileHelper.getFile(fileEntryTest)
        assertTrue(createdFile.exists())


        fromDB!!.apply {
            deleteFile()
            assertFalse(createdFile.exists())
        }
    }
}

val fileEntryTest = FileEntry("Ⓐ", StorageType.global, 1L, "sha256", 0, "fodlr")
