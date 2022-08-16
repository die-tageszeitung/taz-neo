package de.taz.app.android.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import de.taz.app.android.DISPLAYABLE_NAME
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.TestDataUtil
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.Cycle
import de.taz.app.android.api.models.*
import de.taz.app.android.content.ContentService
import de.taz.app.android.data.DataService
import de.taz.app.android.data.DownloadScheduler
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.util.NewIssuePollingScheduler
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
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val OLD_DATE = "2019-01-02"
private const val NEW_DATE = "2019-01-03"


@RunWith(MockitoJUnitRunner::class)
class IssueDownloadWorkManagerWorkerTest {

    private lateinit var context: Context
    private lateinit var executor: Executor


    private val oldFeedMock = Feed(
        DISPLAYABLE_NAME,
        Cycle.daily,
        0.67f,
        publicationDates = listOf(
            requireNotNull(simpleDateFormat.parse(OLD_DATE))
        ),
        OLD_DATE,
        OLD_DATE
    )
    private val newFeedMock = Feed(
        DISPLAYABLE_NAME,
        Cycle.daily,
        0.67f,
        publicationDates = listOf(
            requireNotNull(simpleDateFormat.parse(NEW_DATE)),
            requireNotNull(simpleDateFormat.parse(OLD_DATE))
        ),
        OLD_DATE,
        NEW_DATE
    )

    @Mock
    private lateinit var mockDataService: DataService

    @Mock
    private lateinit var mockApiService: ApiService

    @Mock
    private lateinit var mockContentService: ContentService

    @Mock
    private lateinit var mockDownloadScheduler: DownloadScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        executor = Executors.newSingleThreadExecutor()
        DataService.inject(mockDataService)
        ApiService.inject(mockApiService)
        ContentService.inject(mockContentService)
        DownloadScheduler.inject(mockDownloadScheduler)
    }

    @After
    fun tearDown() {
    }

    @Test
    fun scheduleDownloadWithoutPoll() = runBlocking {
        `when`(
            mockDataService.getFeedByName(
                anyOrNull(),
                eq(true),
                eq(false)
            )
        ).thenReturn(
            oldFeedMock
        )

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
        `when`(mockDataService.refreshFeedAndGetIssueKeyIfNew(anyOrNull())).thenReturn(
            TestDataUtil.getIssue().issueKey
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
