package de.taz.app.android.ui.webview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import de.taz.app.android.R
import de.taz.app.android.TestLifecycleOwner
import de.taz.app.android.api.interfaces.WebViewDisplayable
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

class WebViewPresenterTest {

    @kotlinx.coroutines.ObsoleteCoroutinesApi
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    private lateinit var presenter: WebViewPresenter<WebViewDisplayable>

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @Mock
    lateinit var webViewContractView: WebViewContract.View<WebViewDisplayable>
    @Mock
    lateinit var mainContractView: MainContract.View
    @Mock
    lateinit var viewModel: WebViewDataController<WebViewDisplayable>

    private val webViewDisplayable: WebViewDisplayable = object : WebViewDisplayable {
        override fun getFile(): File? {
            return File("/path/to/exile")
        }

        override fun next(): WebViewDisplayable? {
            return nextWebViewDisplayable
        }

        override fun previous(): WebViewDisplayable? {
            return previousWebViewDisplayable
        }
    }

    private val nextWebViewDisplayable = object : WebViewDisplayable {
        override fun getFile(): File? {
            return File("/path/to/exile")
        }

        override fun next(): WebViewDisplayable? {
            return null
        }

        override fun previous(): WebViewDisplayable? {
            return webViewDisplayable
        }
    }

    private val previousWebViewDisplayable = object : WebViewDisplayable {
        override fun getFile(): File? {
            return File("/path/to/exile")
        }

        override fun next(): WebViewDisplayable? {
            return webViewDisplayable
        }

        override fun previous(): WebViewDisplayable? {
            return null
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
        presenter = WebViewPresenter()

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

    @Test
    fun onSwipeLeft() = runBlocking {
        // show next item if swiping left
        presenter.onSwipeLeft()?.join()
        Mockito.verify(mainContractView).showInWebView(
            eq(nextWebViewDisplayable), eq(R.anim.slide_in_left), eq(R.anim.slide_out_left)
        )
    }

    @Test
    fun onSwipeRight() = runBlocking {
        // show previous item if swiping right
        presenter.onSwipeRight()?.join()
        Mockito.verify(mainContractView).showInWebView(
            eq(previousWebViewDisplayable),
            eq(R.anim.slide_in_right),
            eq(R.anim.slide_out_right)
        )
    }
}