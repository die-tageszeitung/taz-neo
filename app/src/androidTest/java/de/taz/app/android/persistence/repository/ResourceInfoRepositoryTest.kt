package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.AppDatabase
import org.junit.After
import org.junit.Before

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException


@RunWith(AndroidJUnit4::class)
class ResourceInfoRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var resourceInfoRepository: ResourceInfoRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        val fileEntryRepository = FileEntryRepository.createInstance(context)
        fileEntryRepository.appDatabase = db

        resourceInfoRepository = ResourceInfoRepository.getInstance(context)
        resourceInfoRepository.appDatabase = db
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }


    @Test
    @Throws(Exception::class)
    fun writeAndRead() {
        resourceInfoRepository.save(resourceInfo)
        val fromDB = resourceInfoRepository.get()
        assertEquals(fromDB, resourceInfo)
    }

    @Test
    @Throws(Exception::class)
    fun readWithoutFiles() {
        resourceInfoRepository.save(resourceInfo)
        val fromDB = resourceInfoRepository.getWithoutFiles()
        assertEquals(fromDB, ResourceInfoStub(resourceInfo))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() {
        resourceInfoRepository.save(resourceInfo)
        resourceInfoRepository.save(resourceInfo2)

        val fromDB = resourceInfoRepository.get()
        assertEquals(fromDB, resourceInfo2)
    }

    private val resourceFiles = IssueTestUtil.getIssue().sectionList.first().imageList.map { it.copy(folder = RESOURCE_FOLDER) }.map { FileEntry(it) }
    private val resourceFiles2 = IssueTestUtil.getIssue().sectionList[1].imageList.map { it.copy(folder = RESOURCE_FOLDER) }.map { FileEntry(it) }
    private val resourceInfo = ResourceInfo(1, "http://example.com", "1.zip",  resourceFiles, DownloadStatus.pending)
    private val resourceInfo2 = ResourceInfo(2, "http://example.com", "2.zip",  resourceFiles2, DownloadStatus.pending)
}

