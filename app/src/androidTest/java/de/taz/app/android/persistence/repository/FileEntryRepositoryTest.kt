package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.interfaces.StorageType
import de.taz.app.android.persistence.AppDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class FileEntryRepositoryTest {
    private lateinit var db: AppDatabase

    private lateinit var fileEntryRepository: FileEntryRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
                context, AppDatabase::class.java).build()
        fileEntryRepository = FileEntryRepository.getInstance(context)
        fileEntryRepository.appDatabase = db
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeAndRead() {
        fileEntryRepository.save(fileEntryTest)
        val fromDB = fileEntryRepository.get(fileEntryTest.name)
        assertEquals(fromDB, fileEntryTest)
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMulitple() {
        fileEntryRepository.save(fileEntryTest)
        fileEntryRepository.save(fileEntryTest2)
        val fromDB = fileEntryRepository.get(fileEntryTest.name)
        val fromDB2 = fileEntryRepository.get(fileEntryTest2.name)
        assertEquals(fromDB, fileEntryTest)
        assertEquals(fromDB2, fileEntryTest2)
    }

    @Throws(Exception::class)
    fun noMoTimeDowngrade() {
        assertEquals(fileEntryTest.name, fileEntryTestWithLowerMoTime.name)
        fileEntryRepository.save(fileEntryTest)
        fileEntryRepository.save(fileEntryTestWithLowerMoTime)

        val fromDB = fileEntryRepository.get(fileEntryTest.name)

        assertEquals(fromDB, fileEntryTest)
    }

    @Throws(Exception::class)
    fun moTimeUpgrade() {
        assertEquals(fileEntryTest.name, fileEntryTestWithHigherMoTime.name)

        fileEntryRepository.save(fileEntryTest)
        fileEntryRepository.save(fileEntryTestWithHigherMoTime)

        val fromDB = fileEntryRepository.get(fileEntryTest.name)

        assertEquals(fromDB, fileEntryTestWithHigherMoTime)
    }
}

val fileEntryTestWithLowerMoTime = FileEntry("Ⓐ", StorageType.global, 0L, "sha256", 0)
val fileEntryTest = FileEntry("Ⓐ", StorageType.global, 1L, "sha256", 0)
val fileEntryTestWithHigherMoTime = FileEntry("Ⓐ", StorageType.global, 3L, "sha256", 0)
val fileEntryTest2 = FileEntry("☭", StorageType.issue, 1L, "sha256", 0)
