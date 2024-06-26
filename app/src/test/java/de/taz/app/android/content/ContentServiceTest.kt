package de.taz.app.android.content

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.ApiService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.content.cache.CacheOperationItem
import de.taz.app.android.content.cache.FileCacheItem
import de.taz.app.android.download.FileDownloader
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import de.taz.test.TestDataUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class ContentServiceTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
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

    private lateinit var mockApiService: ApiService

    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        AppDatabase.inject(db)

        mockApiService = mock()
        ApiService.inject(mockApiService)

        contentService = ContentService.getInstance(context)
        issueRepository = IssueRepository.getInstance(context)
    }

    @After
    fun tearDown() {
        db.close()
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
        val testIssuePublication = IssuePublication(testIssue.issueKey)
        FileDownloader.inject(catastrophicTestDownloader)
        assert(!issueRepository.isDownloaded(testIssue.issueKey))

        // Call to content service produces exception
        assertFailsWith<CacheOperationFailedException> {
            contentService.downloadToCache(testIssuePublication)
        }

        assert(!issueRepository.isDownloaded(testIssue.issueKey))
    }

    @Test
    fun retrieveIssueWithSomeExceptions() = runTest {
        val testIssuePublication = IssuePublication(testIssue.issueKey)
        FileDownloader.inject(oneFileFailedDownloader)
        val testIssue = TestDataUtil.getIssue()
        assert(!issueRepository.isDownloaded(testIssue.issueKey))

        // Call to content service produces exception
        assertFailsWith<CacheOperationFailedException> {
            contentService.downloadToCache(testIssuePublication)
        }

        assert(!issueRepository.isDownloaded(testIssue.issueKey))
    }
}