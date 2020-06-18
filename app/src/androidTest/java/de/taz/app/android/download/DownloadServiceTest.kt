package de.taz.app.android.download

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.runner.RunWith


const val TEST_STRING = "bla"
const val TEST_FILE_NAME = "bla"

@RunWith(AndroidJUnit4::class)
class DownloadServiceTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var downloadService: DownloadService
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var fileEntryRepository: FileEntryRepository

    private val mockServer = MockWebServer()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()

        downloadService = DownloadService.getInstance(context)

        downloadService.appInfoRepository.appDatabase = db
        downloadService.downloadRepository.appDatabase = db
        downloadService.fileEntryRepository.appDatabase = db
        downloadService.issueRepository.appDatabase = db
        downloadService.resourceInfoRepository.appDatabase = db

        downloadRepository = DownloadRepository.getInstance(context)
        downloadRepository.appDatabase = db
        fileEntryRepository = FileEntryRepository.getInstance(context)
        fileEntryRepository.appDatabase = db

        mockServer.protocols = listOf(Protocol.HTTP_1_1, Protocol.HTTP_2)
        mockServer.start()
        mockServer.url("bla")

        fileEntryRepository.delete(TEST_FILE_NAME)
        downloadRepository.delete(TEST_FILE_NAME)
    }


    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun abortDownloadOn400Response() {
        val mockFileEntry = FileEntry(TEST_FILE_NAME, StorageType.issue, 0, "", 0, "bla")
        val mockDownload =
            Download(mockServer.url("").toString(), mockFileEntry, DownloadStatus.pending)

        fileEntryRepository.saveOrReplace(mockFileEntry)
        downloadRepository.save(mockDownload)

        val mockResponse = MockResponse().setResponseCode(400)
        mockServer.enqueue(mockResponse)

        try {
            runBlocking { downloadService.getFromServer(TEST_FILE_NAME) }

            assertEquals(DownloadStatus.aborted, downloadRepository.get(TEST_FILE_NAME)?.status)
        } catch (e: Exception) {
            fail("Test should not have thrown an exception")
        }
    }

    @Test
    fun successDownloadOn200Response() {
        val mockFileSha = "4df3c3f68fcc83b27e9d42c90431a72499f17875c81a599b566c9889b9696703"
        val mockFileEntry = FileEntry(TEST_FILE_NAME, StorageType.issue, 0, mockFileSha, 0, "bla")
        val mockDownload =
            Download(mockServer.url("").toString(), mockFileEntry, DownloadStatus.pending)

        fileEntryRepository.saveOrReplace(mockFileEntry)
        downloadRepository.save(mockDownload)

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/plain")
            .setBody(TEST_STRING)
        mockServer.enqueue(mockResponse)

        runBlocking { downloadService.getFromServer(TEST_FILE_NAME) }

        assertEquals(DownloadStatus.done, downloadRepository.get(TEST_FILE_NAME)?.status)
    }

    @Test
    fun abortDownloadOnDivergingShaSums() {
        val mockFileEntry = FileEntry(TEST_FILE_NAME, StorageType.issue, 0, "", 0, "bla")
        val mockDownload =
            Download(mockServer.url("").toString(), mockFileEntry, DownloadStatus.pending)

        fileEntryRepository.saveOrReplace(mockFileEntry)
        downloadRepository.save(mockDownload)

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/plain")
            .setBody(TEST_STRING)
        mockServer.enqueue(mockResponse)

        downloadService.getFromServer(TEST_FILE_NAME)

        // TODO replace takeOld once we have proper sha handling
        assertEquals(DownloadStatus.takeOld, downloadRepository.get(TEST_FILE_NAME)?.status)
    }

}