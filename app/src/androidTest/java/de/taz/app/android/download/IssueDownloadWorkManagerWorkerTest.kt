package de.taz.app.android.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import de.taz.app.android.content.ContentService
import de.taz.app.android.data.DataService
import de.taz.app.android.data.DownloadScheduler
import de.taz.app.android.util.NewIssuePollingScheduler
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor
import java.util.concurrent.Executors


@RunWith(MockitoJUnitRunner::class)
class IssueDownloadWorkManagerWorkerTest {

    private lateinit var context: Context
    private lateinit var executor: Executor

    @Mock
    private lateinit var mockFeedService: FeedService

    @Mock
    private lateinit var mockContentService: ContentService

    @Mock
    private lateinit var mockDownloadScheduler: DownloadScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        executor = Executors.newSingleThreadExecutor()
        FeedService.inject(mockFeedService)
        ContentService.inject(mockContentService)
        DownloadScheduler.inject(mockDownloadScheduler)
    }

    @After
    fun tearDown() {
    }

    @Test
    fun scheduleDownloadWithoutPoll() = runBlocking {
        whenever(mockFeedService.getFeedFlowByName(anyString())).thenReturn(flowOf(null))

        `when`(
            mockDataService.getFeedByName(
                anyOrNull(),
                eq(false),
                eq(false)
            )
        ).thenReturn(
            newFeedMock
        )

        `when`(
            mockApiService.getIssueByPublication(anyOrNull())
        ).thenReturn(
            TestDataUtil.getIssue()
        )

        `when`(
            mockApiService.getMomentByFeedAndDate(
                anyOrNull(),
                anyOrNull()
            )
        ).thenReturn(
            Moment(DISPLAYED_FEED, NEW_DATE, IssueStatus.public, "", dateDownload = null)
        )

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
