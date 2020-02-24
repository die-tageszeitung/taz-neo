package de.taz.app.android.ui.archive

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import de.taz.app.android.TestLifecycleOwner
import de.taz.app.android.api.ApiService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.testIssues
import de.taz.app.android.ui.home.page.archive.ArchiveContract
import de.taz.app.android.ui.home.page.archive.ArchiveDataController
import de.taz.app.android.ui.home.page.archive.ArchivePresenter
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.singletons.DateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test

import org.junit.Before
import org.junit.Rule
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.text.SimpleDateFormat

class ArchivePresenterTest {

    @kotlinx.coroutines.ObsoleteCoroutinesApi
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    private lateinit var presenter: ArchivePresenter

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @Mock
    lateinit var archiveContractView: ArchiveContract.View
    @Mock
    lateinit var mainContractView: MainContract.View
    @Mock
    lateinit var viewModel: ArchiveDataController
    @Mock
    lateinit var apiService: ApiService
    @Mock
    lateinit var issueRepository: IssueRepository
    @Mock
    lateinit var dateHelper: DateHelper

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    private val lifecycleOwner = TestLifecycleOwner()

    @Before
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @kotlinx.coroutines.ObsoleteCoroutinesApi
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        MockitoAnnotations.initMocks(this)

        apiService.simpleDateFormat = SimpleDateFormat()
        presenter = ArchivePresenter(
            apiService,
            issueRepository,
            dateHelper
        )
        presenter.attach(archiveContractView)

        Mockito.`when`(archiveContractView.getMainView()).thenReturn(mainContractView)

        presenter.viewModel = viewModel
        Mockito.`when`(mainContractView.getLifecycleOwner()).thenReturn(lifecycleOwner)
        Mockito.`when`(archiveContractView.getLifecycleOwner()).thenReturn(lifecycleOwner)
    }

    @After
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @kotlinx.coroutines.ObsoleteCoroutinesApi
    fun tearDown() {
        Dispatchers.resetMain() // reset main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    @Test
    fun onViewCreated() {
        presenter.onViewCreated(null)

        Mockito.verify(viewModel).observeFeeds(any(), any())
        Mockito.verify(viewModel).observeInactiveFeedNames(any(), any())
        Mockito.verify(viewModel).observeIssueStubs(any(), any())
    }

    @Test
    fun getNextIssueMoments() {
        runBlocking {
            doReturn(testIssues).`when`(apiService).getIssuesByDate(any(), any())

            val date = "2010-01-01"
            val limit = 10
            presenter.downloadNextIssues(date, limit)

            Mockito.verify(apiService).getIssuesByDate(date, limit)
            Mockito.verify(issueRepository).save(testIssues)
        }
    }

    @Test
    fun getNextIssueMomentsFails() {
        runBlocking {
            doThrow(ApiService.ApiServiceException.NoInternetException()).`when`(apiService).getIssuesByDate(any(), any())

            val date = "2010-01-01"
            val limit = 10
            presenter.downloadNextIssues(date, limit)

            Mockito.verify(apiService).getIssuesByDate(date, limit)
            Mockito.verify(mainContractView).showToast(any<@androidx.annotation.StringRes Int>())
        }
    }

}
