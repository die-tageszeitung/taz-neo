package de.taz.app.android.ui.webview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import de.taz.app.android.TestLifecycleOwner
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.testArticle
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

class ArticleWebViewPresenterTest {

    @kotlinx.coroutines.ObsoleteCoroutinesApi
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    private lateinit var presenter: ArticleWebViewPresenter

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @Mock
    lateinit var webViewContractView: WebViewContract.View<Article>
    @Mock
    lateinit var mainContractView: MainContract.View
    @Mock
    lateinit var viewModel: WebViewDataController<Article>
    @Mock
    lateinit var apiService: ApiService
    @Mock
    lateinit var resourceInfoRepository: ResourceInfoRepository

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    private val lifecycleOwner = TestLifecycleOwner()

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @kotlinx.coroutines.ObsoleteCoroutinesApi
    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        MockitoAnnotations.initMocks(this)
        presenter = ArticleWebViewPresenter(apiService, resourceInfoRepository)

        presenter.attach(webViewContractView)

        Mockito.`when`(webViewContractView.getMainView()).thenReturn(mainContractView)

        presenter.viewModel = viewModel

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
        presenter.onLinkClicked(testArticle)

        Mockito.verify(mainContractView).showInWebView(eq(testArticle), any(), any())
    }

}
