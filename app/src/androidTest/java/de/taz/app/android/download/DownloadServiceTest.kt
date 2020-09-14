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
import junit.framework.TestCase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.setMain
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.*
import org.junit.runner.RunWith


const val TEST_STRING = "bla"
const val TEST_FILE_NAME = "bla"

@RunWith(AndroidJUnit4::class)
class DownloadServiceTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @ObsoleteCoroutinesApi
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    private lateinit var downloadService: DownloadService
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var fileEntryRepository: FileEntryRepository

    private val mockServer = MockWebServer()

    @ObsoleteCoroutinesApi
    @ExperimentalCoroutinesApi
    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
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
        downloadService.currentDownloads.set(0)
        downloadService.currentDownloadList.clear()

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
        val mockResponse = MockResponse().setResponseCode(400)
        testAborted(mockResponse)
    }

    @Test
    fun startDownloadIfCapacityIfNoDownloads() {
        runBlocking {
            downloadService.startDownloadIfCapacity()
        }
        assertTrue(downloadService.currentDownloadList.isEmpty())
        assertEquals(0, downloadService.currentDownloads.get())
    }

    @Test
    fun startDownloadsIfCapacityIfNoDownloads() {
        runBlocking {
            downloadService.startDownloadsIfCapacity()
        }
        assertTrue(downloadService.currentDownloadList.isEmpty())
        assertEquals(0, downloadService.currentDownloads.get())
    }

    @Test
    fun successDownloadOn200Response() {
        val mockFileSha = "4df3c3f68fcc83b27e9d42c90431a72499f17875c81a599b566c9889b9696703"
        val mockFileEntry = FileEntry(
            TEST_FILE_NAME,
            StorageType.issue,
            0,
            mockFileSha,
            0,
            "bla",
            DownloadStatus.pending
        )
        val mockDownload =
            Download(mockServer.url("").toString(), mockFileEntry, DownloadStatus.pending)

        fileEntryRepository.saveOrReplace(mockFileEntry)
        downloadRepository.save(mockDownload)

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/plain")
            .setBody(TEST_STRING)
        mockServer.enqueue(mockResponse)

        downloadService.getBlockingFromServer(TEST_FILE_NAME)

        assertEquals(DownloadStatus.done, downloadRepository.get(TEST_FILE_NAME)?.status)
        assertEquals(DownloadStatus.done, fileEntryRepository.get(TEST_FILE_NAME)?.downloadedStatus)
        assertEquals(0, downloadService.currentDownloads.get())
        assertTrue(downloadService.currentDownloadList.isEmpty())
    }

    @Test
    fun abortOnFileEmpty() {
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/plain")
            .setBody("")
        testAborted(mockResponse)
    }

    @Test
    fun takeOldOnDivergingShaSums() {
        val mockFileEntry =
            FileEntry(TEST_FILE_NAME, StorageType.issue, 0, "", 0, "bla", DownloadStatus.pending)
        val mockDownload =
            Download(mockServer.url("").toString(), mockFileEntry, DownloadStatus.pending)

        fileEntryRepository.saveOrReplace(mockFileEntry)
        downloadRepository.save(mockDownload)

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/plain")
            .setBody(TEST_STRING)
        mockServer.enqueue(mockResponse)

        downloadService.getBlockingFromServer(TEST_FILE_NAME)

        assertEquals(DownloadStatus.takeOld, downloadRepository.get(TEST_FILE_NAME)?.status)
        assertEquals(downloadService.currentDownloads.get(), 0)
        assertTrue(downloadService.currentDownloadList.isEmpty())
    }

    @Test
    fun abortOnDisconnectDuringResponse() {
        testAborted(
            MockResponse().also { it.socketPolicy = SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY }
        )
    }


    @Test
    fun abortOnDisconnectDuringRequest() {
        testAborted(
            MockResponse().also { it.socketPolicy = SocketPolicy.DISCONNECT_DURING_REQUEST_BODY }
        )
    }

    @Test
    fun abortOnFailedHandshake() {
        testAborted(
            MockResponse().also { it.socketPolicy = SocketPolicy.FAIL_HANDSHAKE }
        )
    }

    @Test
    fun abortOnDisconnectAtStart() {
        testAborted(
            MockResponse().also { it.socketPolicy = SocketPolicy.DISCONNECT_AT_START }
        )
    }

    @Test
    fun abortOnDisconnectAtEnd() {
        testAborted(
            MockResponse().also { it.socketPolicy = SocketPolicy.DISCONNECT_AT_END }
        )
    }

    @Test
    fun abortOnDisconnectAfterRequest() {
        testAborted(
            MockResponse().also { it.socketPolicy = SocketPolicy.DISCONNECT_AFTER_REQUEST }
        )
    }

    @Test
    fun abortOnStallSocketAtStart() {
        testAborted(
            MockResponse().also { it.socketPolicy = SocketPolicy.STALL_SOCKET_AT_START }
        )
    }

    @Test
    fun abortOnNoResponse() {
        testAborted(
            MockResponse().also { it.socketPolicy = SocketPolicy.NO_RESPONSE }
        )
    }

    @Test
    fun resetOnCancel() {
        val mockFileEntry =
            FileEntry(TEST_FILE_NAME, StorageType.issue, 0, "", 0, "bla", DownloadStatus.pending)
        val mockDownload =
            Download(mockServer.url("").toString(), mockFileEntry, DownloadStatus.pending, "tag")

        fileEntryRepository.saveOrReplace(mockFileEntry)
        downloadRepository.save(mockDownload)

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/plain")
            .setBody(TEST_STRING)
        mockServer.enqueue(mockResponse)

        downloadService.downloadList.add(mockDownload)
        runBlocking {
            launch {
                val job = downloadService.startDownloadIfCapacity()
                downloadService.cancelDownloadsForTag("tag")
                job?.join()
            }.join()
        }
        assertEquals(DownloadStatus.pending, downloadRepository.get(TEST_FILE_NAME)?.status)
        assertEquals(0, downloadService.currentDownloads.get())
        assertTrue(downloadService.currentDownloadList.isEmpty())
    }

    private fun testAborted(mockResponse: MockResponse) {
        val mockFileEntry = FileEntry(
            TEST_FILE_NAME,
            StorageType.issue,
            0,
            "",
            0,
            "bla",
            DownloadStatus.pending
        )
        val mockDownload =
            Download(mockServer.url("").toString(), mockFileEntry, DownloadStatus.pending)

        fileEntryRepository.saveOrReplace(mockFileEntry)
        downloadRepository.save(mockDownload)

        mockServer.enqueue(mockResponse)

        downloadService.getBlockingFromServer(TEST_FILE_NAME)

        assertEquals(DownloadStatus.aborted, downloadRepository.get(TEST_FILE_NAME)?.status)
        assertEquals(
            DownloadStatus.aborted,
            fileEntryRepository.get(TEST_FILE_NAME)?.downloadedStatus
        )
        assertEquals(0, downloadService.currentDownloads.get())
        assertTrue(downloadService.currentDownloadList.isEmpty())
    }

    @Test
    fun downloadAlreadyDownloadedFileEntry() {
        val mockFileSha = "4df3c3f68fcc83b27e9d42c90431a72499f17875c81a599b566c9889b9696703"
        val mockFileEntry = FileEntry(
            TEST_FILE_NAME,
            StorageType.issue,
            0,
            mockFileSha,
            0,
            "bla",
            DownloadStatus.done
        )
        val mockDownload =
            Download(mockServer.url("").toString(), mockFileEntry, DownloadStatus.done)

        fileEntryRepository.saveOrReplace(mockFileEntry)
        downloadRepository.save(mockDownload)

        downloadService.getBlockingFromServer(TEST_FILE_NAME)

        assertEquals(DownloadStatus.done, downloadRepository.get(TEST_FILE_NAME)?.status)
        assertEquals(DownloadStatus.done, fileEntryRepository.get(TEST_FILE_NAME)?.downloadedStatus)
        assertEquals(0, downloadService.currentDownloads.get())
        assertTrue(downloadService.currentDownloadList.isEmpty())
    }

    @Test
    fun downloadAlreadyDownloadingFileEntry() {
        val mockFileSha = "4df3c3f68fcc83b27e9d42c90431a72499f17875c81a599b566c9889b9696703"
        val mockFileEntry = FileEntry(
            TEST_FILE_NAME,
            StorageType.issue,
            0,
            mockFileSha,
            0,
            "bla",
            DownloadStatus.started
        )
        val mockDownload =
            Download(mockServer.url("").toString(), mockFileEntry, DownloadStatus.started)

        fileEntryRepository.saveOrReplace(mockFileEntry)
        downloadRepository.save(mockDownload)

        downloadService.getBlockingFromServer(TEST_FILE_NAME)

        assertEquals(DownloadStatus.started, downloadRepository.get(TEST_FILE_NAME)?.status)
        assertEquals(DownloadStatus.started, fileEntryRepository.get(TEST_FILE_NAME)?.downloadedStatus)
        assertEquals(0, downloadService.currentDownloads.get())
        assertTrue(downloadService.currentDownloadList.isEmpty())
    }
    @Test
    fun downloadWithLastSHA256Matching() {
        val mockFileSha = "4df3c3f68fcc83b27e9d42c90431a72499f17875c81a599b566c9889b9696703"
        val mockFileEntry = FileEntry(
            TEST_FILE_NAME,
            StorageType.issue,
            0,
            mockFileSha,
            0,
            "bla",
            DownloadStatus.pending
        )
        val mockDownload =
            Download(
                mockServer.url("").toString(),
                mockFileEntry,
                DownloadStatus.pending,
                null,
                mockFileSha
            )

        fileEntryRepository.saveOrReplace(mockFileEntry)
        downloadRepository.save(mockDownload)

        downloadService.getBlockingFromServer(TEST_FILE_NAME)

        assertEquals(DownloadStatus.done, downloadRepository.get(TEST_FILE_NAME)?.status)
        assertEquals(DownloadStatus.done, fileEntryRepository.get(TEST_FILE_NAME)?.downloadedStatus)
        assertEquals(0, downloadService.currentDownloads.get())
        assertTrue(downloadService.currentDownloadList.isEmpty())
    }
}