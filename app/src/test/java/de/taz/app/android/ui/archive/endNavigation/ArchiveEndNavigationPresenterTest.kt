package de.taz.app.android.ui.archive.endNavigation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import de.taz.app.android.TestLifecycleOwner
import de.taz.app.android.testFeed
import de.taz.app.android.ui.home.feedFilter.FeedFilterContract
import de.taz.app.android.ui.home.feedFilter.FeedFilterDataController
import de.taz.app.android.ui.home.feedFilter.FeedFilterPresenter
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.util.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

import org.junit.Rule
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class ArchiveEndNavigationPresenterTest {

    @kotlinx.coroutines.ObsoleteCoroutinesApi
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    private lateinit var presenter: FeedFilterPresenter

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @Mock
    lateinit var contractView: FeedFilterContract.View
    @Mock
    lateinit var mainContractView: MainContract.View
    @Mock
    lateinit var viewModel: FeedFilterDataController
    @Mock
    lateinit var preferencesHelper: PreferencesHelper


    @kotlinx.coroutines.ExperimentalCoroutinesApi
    private val lifecycleOwner = TestLifecycleOwner()


    @Before
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @kotlinx.coroutines.ObsoleteCoroutinesApi
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        MockitoAnnotations.initMocks(this)

        presenter =
            FeedFilterPresenter(
                preferencesHelper
            )
        presenter.attach(contractView)
        presenter.viewModel = viewModel

        Mockito.`when`(contractView.getLifecycleOwner()).thenReturn(lifecycleOwner)
        Mockito.`when`(contractView.getMainView()).thenReturn(mainContractView)

        Mockito.`when`(mainContractView.getLifecycleOwner()).thenReturn(lifecycleOwner)
    }

    @After
    fun tearDown() {
    }

    @Test
    fun onViewCreated() {
        presenter.onViewCreated(null)
        Mockito.verify(viewModel).observeFeeds(any(), any())
        Mockito.verify(viewModel).observeInactiveFeedNames(any(), any())
    }

    @Test
    fun onActiveFeedClicked() {
        presenter.onFeedClicked(testFeed)
        Mockito.verify(preferencesHelper).deactivateFeed(testFeed)
    }

    @Test
    fun ondeactivatedFeedClicked() {
        doReturn(setOf(testFeed.name)).`when`(viewModel).getInactiveFeedNames()
        presenter.onFeedClicked(testFeed)
        Mockito.verify(preferencesHelper).activateFeed(testFeed)
    }
}