package de.taz.app.android.ui.archive

import android.graphics.Bitmap
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import de.taz.app.android.TestLifecycleOwner
import de.taz.app.android.api.ApiService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.archive.main.ArchiveContract
import de.taz.app.android.ui.archive.main.ArchiveDataController
import de.taz.app.android.ui.archive.main.ArchivePresenter
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.ui.main.MainDataController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test

import org.junit.Before
import org.junit.Rule
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

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
    lateinit var mainViewModel: MainDataController
    @Mock
    lateinit var apiService: ApiService
    @Mock
    lateinit var issueRepository: IssueRepository
    @Mock
    lateinit var bitmap: Bitmap

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    private val lifecycleOwner = TestLifecycleOwner()

    @Before
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @kotlinx.coroutines.ObsoleteCoroutinesApi
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        MockitoAnnotations.initMocks(this)
        presenter = ArchivePresenter(apiService, issueRepository)

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
    fun onMomentBitmapCreated() {
        val tag = "asdf/01.01.1900"
        presenter.onMomentBitmapCreated(tag, bitmap)

        Mockito.verify(archiveContractView).addBitmap(tag, bitmap)
        Mockito.verify(viewModel).addBitmap(tag, bitmap)
    }

    @Test
    fun onRefresh() {
        presenter.onRefresh()

        Mockito.verify(archiveContractView).hideRefreshLoadingIcon()
    }

}