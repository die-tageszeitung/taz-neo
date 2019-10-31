package de.taz.app.android.persistence.repository

import org.junit.Assert.*

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.DownloadStub
import de.taz.app.android.download.RESOURCE_FOLDER
import de.taz.app.android.persistence.AppDatabase
import kotlinx.io.IOException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class DownloadRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var downloadRepository: DownloadRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
                context, AppDatabase::class.java).build()

        val fileEntryRepository = FileEntryRepository.createInstance(context)
        fileEntryRepository.appDatabase = db

        downloadRepository = DownloadRepository.getInstance(context)
        downloadRepository.appDatabase = db
        fileEntryRepository.save(download.file)
        fileEntryRepository.save(download2.file)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeAndRead() {
        downloadRepository.save(download)
        val fromDB = downloadRepository.get(download.file.name)
        assertEquals(fromDB, download)
    }

    @Test
    @Throws(Exception::class)
    fun readBase() {
        downloadRepository.save(download2)
        val fromDB = downloadRepository.getWithoutFile(download2.file.name)
        assertEquals(fromDB, DownloadStub(download2))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() {
        downloadRepository.save(download)
        val fromDB = downloadRepository.get(download.file.name)
        downloadRepository.save(download2)
        val fromDB2 = downloadRepository.get(download2.file.name)
        assertEquals(fromDB, download)
        assertEquals(fromDB2, download2)
    }

}

private val download = Download("http://example.com/", RESOURCE_FOLDER, fileEntryTest, DownloadStatus.started, UUID.randomUUID())
private val download2 = Download("http://example.com", "another/folder/", fileEntryTest2, DownloadStatus.done)