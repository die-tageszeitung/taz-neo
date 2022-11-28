package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.persistence.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
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
    fun writeAndRead() = runTest {
        fileEntryRepository.save(fileEntryTest)
        val fromDB = fileEntryRepository.get(fileEntryTest.name)
        assertEquals(fromDB, fileEntryTest)
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMulitple() = runTest {
        fileEntryRepository.save(fileEntryTest)
        fileEntryRepository.save(fileEntryTest2)
        val fromDB = fileEntryRepository.get(fileEntryTest.name)
        val fromDB2 = fileEntryRepository.get(fileEntryTest2.name)
        assertEquals(fromDB, fileEntryTest)
        assertEquals(fromDB2, fileEntryTest2)
    }

    @Throws(Exception::class)
    fun noMoTimeDowngrade() = runTest {
        assertEquals(fileEntryTest.name, fileEntryTestWithLowerMoTime.name)
        fileEntryRepository.save(fileEntryTest)
        fileEntryRepository.save(fileEntryTestWithLowerMoTime)

        val fromDB = fileEntryRepository.get(fileEntryTest.name)

        assertEquals(fromDB, fileEntryTest)
    }

    @Throws(Exception::class)
    fun moTimeUpgrade() = runTest {
        assertEquals(fileEntryTest.name, fileEntryTestWithHigherMoTime.name)

        fileEntryRepository.save(fileEntryTest)
        fileEntryRepository.save(fileEntryTestWithHigherMoTime)

        val fromDB = fileEntryRepository.get(fileEntryTest.name)

        assertEquals(fromDB, fileEntryTestWithHigherMoTime)
    }

    @Test
    @Throws(Exception::class)
    fun delete() = runTest {
        fileEntryRepository.save(fileEntryTest)
        val fromDBBefore = fileEntryRepository.get(fileEntryTest.name)
        assertEquals(fileEntryTest, fromDBBefore)

        fileEntryRepository.delete(fileEntryTest)
        val fromDBAfter = fileEntryRepository.get(fileEntryTest.name)
        assertNull(fromDBAfter)
    }
}

val fileEntryTestWithLowerMoTime = FileEntry("Ⓐ", StorageType.global, 0L, "sha256", 0, "folder1", null, "folder1/Ⓐ", StorageLocation.INTERNAL)
val fileEntryTest = FileEntry("Ⓐ", StorageType.global, 1L, "sha256", 0, "folder2", null, "folder2/Ⓐ", StorageLocation.INTERNAL)
val fileEntryTestWithHigherMoTime = FileEntry("Ⓐ", StorageType.global, 3L, "sha256", 0, "folder3", null, "folder3/Ⓐ", StorageLocation.INTERNAL)
val fileEntryTest2 = FileEntry("☭", StorageType.issue, 1L, "sha256", 0, "folder4", null, "folder4/☭", StorageLocation.INTERNAL)
