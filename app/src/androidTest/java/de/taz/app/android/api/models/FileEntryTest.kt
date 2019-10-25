package de.taz.app.android.api.models

import android.content.Context
import android.webkit.CookieSyncManager.createInstance
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.api.interfaces.StorageType
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.FileHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileEntryTest {

    private lateinit var fileHelper: FileHelper
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
        fileHelper = FileHelper.createInstance(context)
    }

    @Test
    @Throws(Exception::class)
    fun delete() {
        fileEntryRepository.save(fileEntryTest)
        val fromDB = fileEntryRepository.get(fileEntryTest.name)
        assertEquals(fileEntryTest, fromDB)

        val file = fileHelper.getFile(fromDB!!.name)
        file.createNewFile()
        assertTrue(file.exists())

        fromDB.delete(file.absolutePath)
        assertNull(fileEntryRepository.get(fromDB.name))
        assertFalse(fileHelper.getFile(fromDB.name).exists())
    }
}

val fileEntryTest = FileEntry("Ⓐ", StorageType.global, 1L, "sha256", 0)
