package de.taz.app.android.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import de.taz.app.android.DISPLAYABLE_NAME
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.api.dto.Cycle
import de.taz.app.android.api.models.*
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.util.NewIssuePollingScheduler
import de.taz.app.android.util.any
import io.ktor.client.*
import io.ktor.client.engine.mock.*
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
    private lateinit var mockDownloadService: DownloadService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        executor = Executors.newSingleThreadExecutor()
        DataService.inject(mockDataService)
        DownloadService.inject(mockDownloadService)
    }

    @After
    fun tearDown() {
    }

    @Test
    fun scheduleDownloadWithoutPoll() = runBlocking {
        `when`(mockDataService.getFeedByName(any(String::class.java), eq(true), eq(false))).thenReturn(
            oldFeedMock
        )
        `when`(mockDataService.getFeedByName(any(String::class.java), eq(false), eq(false))).thenReturn(
            newFeedMock
        )
        `when`(mockDataService.getIssue(any(IssuePublication::class.java), eq(false), eq(false), eq(false), eq(false))).thenReturn(
            IssueTestUtil.getIssue()
        )
        `when`(mockDataService.getMoment(any(IssuePublication::class.java), eq(true), eq(false))).thenReturn(
            Moment(DISPLAYED_FEED, NEW_DATE, IssueStatus.public, "", dateDownload = null)
        )

        val worker = TestListenableWorkerBuilder<IssueDownloadWorkManagerWorker>(
            context = context,
            inputData = workDataOf(KEY_SCHEDULE_NEXT to false)
        ).build()

        val result = runBlocking {
            worker.doWork()
        }

        verify(mockDownloadService, times(0)).scheduleNewestIssueDownload(
            any(String::class.java),
            any(Boolean::class.java),
            any(Long::class.java)
        )
        MatcherAssert.assertThat(result, `is`(ListenableWorker.Result.success()))
    }

    @Test
    fun pollNewIssue() = runBlocking {
        `when`(mockDataService.refreshFeedAndGetIssueIfNew(any(String::class.java))).thenReturn(
            IssueTestUtil.getIssue()
        )
        `when`(mockDataService.getMoment(any(IssuePublication::class.java), eq(true), eq(false))).thenReturn(
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
        verify(mockDownloadService).scheduleNewestIssueDownload(
            any(String::class.java),
            eq(true),
            any(Long::class.java)
        )
        MatcherAssert.assertThat(result, `is`(ListenableWorker.Result.success()))
    }
}
