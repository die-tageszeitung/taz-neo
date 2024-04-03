package de.taz.app.android.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.FeedService
import de.taz.app.android.data.DownloadScheduler
import de.taz.app.android.util.NewIssuePollingScheduler
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import java.util.concurrent.Executors


@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class IssueDownloadWorkManagerWorkerTest {

    private lateinit var context: Context
    private lateinit var executor: Executor

    private lateinit var mockFeedService: FeedService
    private lateinit var mockContentService: ContentService
    private lateinit var mockDownloadScheduler: DownloadScheduler

    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        context = ApplicationProvider.getApplicationContext()
        executor = Executors.newSingleThreadExecutor()
        mockFeedService = mock()
        FeedService.inject(mockFeedService)

        mockContentService = mock()
        ContentService.inject(mockContentService)

        mockDownloadScheduler = mock()
        DownloadScheduler.inject(mockDownloadScheduler)
    }

    @After
    fun tearDown() {
    }

    @Test
    fun scheduleDownloadWithoutPoll() = runBlocking {
        whenever(mockFeedService.getFeedFlowByName(anyString())).thenReturn(flowOf(null))

        val worker = TestListenableWorkerBuilder<IssueDownloadWorkManagerWorker>(
            context = context,
            inputData = workDataOf(KEY_SCHEDULE_NEXT to false)
        ).build()

        val result = runBlocking {
            worker.doWork()
        }

        verify(mockDownloadScheduler, times(0)).scheduleNewestIssueDownload(
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        MatcherAssert.assertThat(result, `is`(ListenableWorker.Result.success()))
    }

    @Test
    fun pollNewIssue() = runBlocking {
        whenever(mockFeedService.getFeedFlowByName(anyString())).thenReturn(flowOf(null))

        val worker = TestListenableWorkerBuilder<IssueDownloadWorkManagerWorker>(
            context = context,
            inputData = workDataOf(KEY_SCHEDULE_NEXT to true)
        ).build()

        val result = runBlocking {
            worker.doWork()
        }

        val nextPollDelay = NewIssuePollingScheduler.getDelayForNextPoll()

        assert(nextPollDelay > 0)
        verify(mockDownloadScheduler).scheduleNewestIssueDownload(
            anyOrNull(),
            eq(true),
            anyOrNull()
        )
        MatcherAssert.assertThat(result, `is`(ListenableWorker.Result.success()))
    }
}
