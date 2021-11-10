package de.taz.app.android.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.TestDataUtil
import de.taz.app.android.api.ApiService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.content.cache.CacheOperationItem
import de.taz.app.android.content.cache.FileCacheItem
import de.taz.app.android.download.FileDownloader
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.any
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doReturn
import org.mockito.junit.MockitoJUnitRunner
import java.io.IOException

@RunWith(MockitoJUnitRunner::class)
class ContentServiceTest {

    private lateinit var context: Context
    private lateinit var contentService: ContentService
    private lateinit var issueRepository: IssueRepository

    private val catastrophicTestDownloader = object : TestFileDownloader() {
        override suspend fun fakeDownloadItem(item: CacheOperationItem<FileCacheItem>) {
            item.operation.apply {
                notifyFailedItem(IOException("Bad file"))
            }
        }
    }

    private val reliableTestDownloader = object : TestFileDownloader() {
        override suspend fun fakeDownloadItem(item: CacheOperationItem<FileCacheItem>) {
            item.operation.apply {
                notifySuccessfulItem()
            }
        }
    }

    private val oneFileFailedDownloader = object : TestFileDownloader() {
        private var failed = 0

        override suspend fun fakeDownloadItem(item: CacheOperationItem<FileCacheItem>) {
            item.operation.apply {
                if (failed < 1) {
                    failed++
                    notifyFailedItem(IOException("Bad file"))
                } else {
                    notifySuccessfulItem()
                }
            }
        }
    }

    private val testIssue = TestDataUtil.getIssue()

    @Mock
    private lateinit var mockApiService: ApiService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ApiService.inject(mockApiService)

        contentService = ContentService.getInstance(context)
        issueRepository = IssueRepository.getInstance(context)


        runBlocking {
            // stupid replication of the retry on connection failure method
            `when`(mockApiService.retryOnConnectionFailure(
                any(Function::class.java) as suspend () -> Unit,
                any(Function::class.java) as suspend () -> Any
            )).then {
                runBlocking { (it.arguments[1] as suspend () -> Any).invoke() }
            }

            doReturn(testIssue)
                .`when`(mockApiService).getIssueByPublication(
                    any(IssuePublication::class.java)
                )
            doReturn(TestDataUtil.getResourceInfo())
                .`when`(mockApiService).getResourceInfo()
        }

    }

    @Test
    fun retrieveIssueWithNoConnectionIssues() {
        FileDownloader.inject(reliableTestDownloader)
        assert(!issueRepository.isDownloaded(testIssue.issueKey))

        // Call to content service ends without exception
        runBlocking {
            contentService.downloadToCacheIfNotPresent(testIssue.issueKey)

        }
        assert(issueRepository.isDownloaded(testIssue.issueKey))
    }

    @Test
    fun retrieveIssueWithExceptions() {
        FileDownloader.inject(catastrophicTestDownloader)
        assert(!issueRepository.isDownloaded(testIssue.issueKey))

        // Call to content service produces exception
        Assert.assertThrows(
            CacheOperationFailedException::class.java
        ) {
            runBlocking { contentService.downloadToCacheIfNotPresent(testIssue.issueKey) }
        }

        assert(!issueRepository.isDownloaded(testIssue.issueKey))
    }

    @Test
    fun retrieveIssueWithSomeExceptions() {
        FileDownloader.inject(oneFileFailedDownloader)
        val testIssue = TestDataUtil.getIssue()
        assert(!issueRepository.isDownloaded(testIssue.issueKey))

        // Call to content service produces exception
        Assert.assertThrows(
            CacheOperationFailedException::class.java
        ) {
            runBlocking { contentService.downloadToCacheIfNotPresent(testIssue.issueKey) }
        }

        assert(!issueRepository.isDownloaded(testIssue.issueKey))
    }
}