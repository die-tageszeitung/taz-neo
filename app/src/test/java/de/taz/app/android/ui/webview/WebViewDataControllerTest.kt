package de.taz.app.android.ui.webview

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import de.taz.app.android.TestLifecycleOwner
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.FileEntry
import org.junit.After
import org.junit.Before
import org.junit.Test

import org.junit.Assert.*
import org.junit.Rule
import org.mockito.MockitoAnnotations
import java.io.File

class WebViewDataControllerTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var webViewDataController: WebViewDataController<WebViewDisplayable>

    private val webViewDisplayable: WebViewDisplayable = object : WebViewDisplayable {
        override fun getFile(): File? { return File("/path/to/exile") }
        override fun next(): WebViewDisplayable? { return null }
        override fun previous(): WebViewDisplayable? { return null }
        override fun getAllFiles(): List<FileEntry> { return emptyList() }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        webViewDataController = WebViewDataController()

    }

    @After
    fun tearDown() {
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun observeWebViewDisplayable() {
        var called = false
        webViewDataController.observeWebViewDisplayable(TestLifecycleOwner()) { displayable ->
            if (displayable != null) {
                called = true
            }
        }

        assertFalse(called)

        webViewDataController.setWebViewDisplayable(webViewDisplayable)
        assertTrue(called)
    }

    @Test
    fun webViewDisplayable() {
        assertNull(webViewDataController.webViewDisplayable.value)
        webViewDataController.setWebViewDisplayable(webViewDisplayable)
        assertEquals(webViewDataController.getWebViewDisplayable(), webViewDisplayable)
    }

}