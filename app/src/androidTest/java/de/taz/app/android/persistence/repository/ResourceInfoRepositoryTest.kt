package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.api.models.ResourceInfoWithoutFiles
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
        resourceInfoRepository = ResourceInfoRepository(db)
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
        assertEquals(fromDB, ResourceInfoWithoutFiles(resourceInfo))
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadMultiple() {
        resourceInfoRepository.save(resourceInfo)
        resourceInfoRepository.save(resourceInfo2)

        val fromDB = resourceInfoRepository.get()
        assertEquals(fromDB, resourceInfo)
    }

    private val resourceFiles = IssueTestUtil.createIssue().sectionList.first().imageList
    private val resourceFiles2 = IssueTestUtil.createIssue().sectionList[1].imageList
    private val resourceInfo = ResourceInfo(1, "http://example.com", "1.zip",  resourceFiles)
    private val resourceInfo2 = ResourceInfo(2, "http://example.com", "2.zip",  resourceFiles2)
}

