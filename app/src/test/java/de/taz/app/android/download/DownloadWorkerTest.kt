package de.taz.app.android.download

import android.util.Log
import androidx.work.WorkManager
import com.nhaarman.mockitokotlin2.*
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.FileHelper
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.mockito.Mockito.inOrder



@RunWith(PowerMockRunner::class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest(Log::class)
class DownloadWorkerTest {

    private val mockServer = MockWebServer()
    @Mock private lateinit var httpClient: OkHttpClient
    @Mock private lateinit var downloadRepository: DownloadRepository
    @Mock private lateinit var fileEntryRepository: FileEntryRepository
    @Mock private lateinit var fileHelper: FileHelper
    @Mock private lateinit var workManager: WorkManager

    private lateinit var downloadWorker : DownloadWorker

    @Before
    fun setUp() {
        PowerMockito.mockStatic(Log::class.java)
        MockitoAnnotations.initMocks(this)
        downloadWorker = DownloadWorker(
            httpClient,
            downloadRepository,
            fileEntryRepository,
            fileHelper,
            workManager
        )
        mockServer.start()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun abortDownloadOn400Response(){
        val mockFileEntry = FileEntry("bla", StorageType.issue, 0, "", 0, "bla")
        val mockDownload = Download("bla", mockFileEntry, DownloadStatus.pending)
        val mockResponse = MockResponse().setResponseCode(400)
        mockServer.enqueue(mockResponse)

        doReturn(mockFileEntry)
            .`when`(fileEntryRepository).get("test")

        doReturn(mockDownload)
            .`when`(downloadRepository).get("test")

        runBlocking { downloadWorker.startDownload("test") }

        inOrder(downloadRepository).apply {
            verify(downloadRepository).setStatus(mockDownload, DownloadStatus.aborted)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun successfulDownloadOn200Response() {
        val mockFileEntry = FileEntry("bla", StorageType.issue, 0, "", 0, "bla")
        val mockDownload = Download(mockServer.url("").toString(), mockFileEntry, DownloadStatus.pending)
        val mockFile = mock<File>()

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/plain")
            .setBody("blabla")
        mockServer.enqueue(mockResponse)

        doReturn(mockFileEntry)
            .`when`(fileEntryRepository).get("test")

        doReturn(mockDownload)
            .`when`(downloadRepository).get("test")

        doReturn(mockFile)
            .`when`(fileHelper).getFile(mockFileEntry)

        doReturn(true)
            .`when`(fileHelper).createFileDirs(mockFileEntry)

        doNothing()
            .`when`(mockFile).writeBytes(any())

        runBlocking { downloadWorker.startDownload("test") }

        inOrder(downloadRepository).apply {
            verify(downloadRepository).setStatus(mockDownload, DownloadStatus.done)
            verifyNoMoreInteractions()
        }
    }

}