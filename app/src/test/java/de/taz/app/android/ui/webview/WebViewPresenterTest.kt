package de.taz.app.android.ui.webview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import de.taz.app.android.TestLifecycleOwner
import de.taz.app.android.api.interfaces.StorageType
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.ui.main.MainContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*

import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File

val TEST_FILE = File("/path/to/exile")
val TEST_FILE_ENTRY = FileEntry("name", StorageType.global, 0L, "sha256", 1L)
val TEST_FILE_ENTRY_LIST = listOf(TEST_FILE_ENTRY)

class WebViewPresenterTest {

    @kotlinx.coroutines.ObsoleteCoroutinesApi
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    private lateinit var presenter: WebViewPresenter<WebViewDisplayable>

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @Mock
    lateinit var articleRepository: ArticleRepository
    @Mock
    lateinit var webViewContractView: WebViewContract.View<WebViewDisplayable>
    @Mock
    lateinit var mainContractView: MainContract.View
    @Mock
    lateinit var viewModel: WebViewDataController<WebViewDisplayable>

    private val webViewDisplayable: WebViewDisplayable = object : WebViewDisplayable {

        override fun getFile(): File? {
            return TEST_FILE
        }

        override fun next(): WebViewDisplayable? {
            return nextWebViewDisplayable
        }

        override fun previous(): WebViewDisplayable? {
            return previousWebViewDisplayable
        }

        override fun getAllFiles(): List<FileEntry> {
            return TEST_FILE_ENTRY_LIST
        }
    }

    private val nextWebViewDisplayable = object : WebViewDisplayable {

        override fun getFile(): File? {
            return TEST_FILE
        }

        override fun next(): WebViewDisplayable? {
            return null
        }

        override fun previous(): WebViewDisplayable? {
            return webViewDisplayable
        }

        override fun getAllFiles(): List<FileEntry> {
            return TEST_FILE_ENTRY_LIST
        }
    }

    private val previousWebViewDisplayable = object : WebViewDisplayable {
        override fun getFile(): File? {
            return TEST_FILE
        }

        override fun next(): WebViewDisplayable? {
            return webViewDisplayable
        }

        override fun previous(): WebViewDisplayable? {
            return null
        }

        override fun getAllFiles(): List<FileEntry> {
            return TEST_FILE_ENTRY_LIST
        }
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    private val lifecycleOwner = TestLifecycleOwner()

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @kotlinx.coroutines.ObsoleteCoroutinesApi
    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        MockitoAnnotations.initMocks(this)
        presenter = WebViewPresenter(articleRepository)

        presenter.attach(webViewContractView)

        Mockito.`when`(webViewContractView.getMainView()).thenReturn(mainContractView)

        presenter.viewModel = viewModel
        Mockito.`when`(viewModel.getWebViewDisplayable()).thenReturn(webViewDisplayable)

        Mockito.`when`(webViewContractView.getLifecycleOwner()).thenReturn(lifecycleOwner)
        Mockito.`when`(mainContractView.getLifecycleOwner()).thenReturn(lifecycleOwner)
    }

    @After
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @kotlinx.coroutines.ObsoleteCoroutinesApi
    fun tearDown() {
        Dispatchers.resetMain() // reset main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    @Test
    fun getView() = runBlocking {
        Assert.assertEquals(presenter.getView(), webViewContractView)
        Assert.assertEquals(presenter.getView()?.getMainView(), mainContractView)
    }


    @Test
    fun onLinkClicked() {
        presenter.onLinkClicked(webViewDisplayable)

        Mockito.verify(mainContractView).showInWebView(eq(webViewDisplayable), any(), any())
    }

    @Test
    fun hideLoadingScreen() {
        // hide loading screen when page has finished loading
        presenter.onPageFinishedLoading()
        Mockito.verify(webViewContractView, Mockito.times(1)).hideLoadingScreen()
    }

}