package de.taz.app.android.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.TestDataUtil
import de.taz.app.android.api.ApiService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.content.cache.CacheOperationItem
import de.taz.app.android.content.cache.FileCacheItem
import de.taz.app.android.download.FileDownloader
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import java.io.IOException
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
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
    }


    @Test
    fun retrieveIssueWithNoConnectionIssues() = runTest {
        /* TODO this test crashes our tests - fix another day
        FileDownloader.inject(reliableTestDownloader)
        assert(!issueRepository.isDownloaded(testIssue.issueKey))

        whenever(mockApiService.retryOnConnectionFailure(anyOrNull(), anyOrNull(), anyOrNull<suspend () -> Any>())).then {
            runBlocking { (it.arguments[2] as suspend () -> Any).invoke() }
        }
        doReturn(testIssue)
            .`when`(mockApiService).getIssueByPublication(anyOrNull())
        doReturn(TestDataUtil.getResourceInfo())
            .`when`(mockApiService).getResourceInfo()

        // Call to content service ends without exception
        contentService.downloadToCache(testIssue.issueKey)
        assert(issueRepository.isDownloaded(testIssue.issueKey))
        */
    }

    @Test
    fun retrieveIssueWithExceptions() = runTest {
        FileDownloader.inject(catastrophicTestDownloader)
        assert(!issueRepository.isDownloaded(testIssue.issueKey))

        // Call to content service produces exception
        assertFailsWith<CacheOperationFailedException> {
            contentService.downloadToCache(testIssue.issueKey)
        }

        assert(!issueRepository.isDownloaded(testIssue.issueKey))
    }

    @Test
    fun retrieveIssueWithSomeExceptions() = runTest {
        FileDownloader.inject(oneFileFailedDownloader)
        val testIssue = TestDataUtil.getIssue()
        assert(!issueRepository.isDownloaded(testIssue.issueKey))

        // Call to content service produces exception
        assertFailsWith<CacheOperationFailedException> {
            contentService.downloadToCache(testIssue.issueKey)
        }

        assert(!issueRepository.isDownloaded(testIssue.issueKey))
    }
}