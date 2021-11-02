package de.taz.app.android.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.content.cache.CacheOperationItem
import de.taz.app.android.content.cache.FileCacheItem
import de.taz.app.android.download.FileDownloader
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.io.IOException
import kotlin.random.Random

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

    private val chaoticTestDownloader = object : TestFileDownloader() {
        private val failProbability = 0.5
        override suspend fun fakeDownloadItem(item: CacheOperationItem<FileCacheItem>) {

            item.operation.apply {
                if (failProbability > Random.nextFloat()) {
                    notifyFailedItem(IOException("Bad file"))
                } else {
                    notifySuccessfulItem()
                }
            }
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        contentService = ContentService.getInstance(context)
        issueRepository = IssueRepository.getInstance(context)
    }

    @Test
    fun retrieveIssueWithNoConnectionIssues() {
        FileDownloader.inject(reliableTestDownloader)
        val testIssue = IssueTestUtil.getIssue()
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
        val testIssue = IssueTestUtil.getIssue()
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
        FileDownloader.inject(chaoticTestDownloader)
        val testIssue = IssueTestUtil.getIssue()
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