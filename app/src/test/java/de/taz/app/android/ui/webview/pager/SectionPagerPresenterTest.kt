package de.taz.app.android.ui.webview.pager

import com.nhaarman.mockitokotlin2.*
import de.taz.app.android.api.models.Section
import de.taz.app.android.ui.main.MainContract
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class SectionPagerPresenterTest {

    @Mock
    lateinit var mainView: MainContract.View

    @Mock
    lateinit var view: SectionPagerContract.View

    @Mock
    lateinit var dataController: SectionPagerDataController
    //    lateinit var dataController: SectionPagerContract.DataController

    val presenter = SectionPagerPresenter()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this);
        presenter.attach(view)

        presenter.viewModel = dataController
        whenever(view.getMainView()).thenReturn(mainView)
    }

    // FIXME: i am not happy about testing livedata inside the presenter.
    // not really testable. requires a LOT of boiler code to mock...

    @Test
    fun testSetInitialSection() {
        val section: Section =  mock()
        presenter.setInitialSection(section)
        verify(dataController).setInitialSection(section)
    }

    @Test
    fun testSetCurrentPosition() {
        presenter.setCurrentPosition(23)
        verify(dataController).currentPosition = 23
    }

    @Test
    fun testTrySetSectionTrue() {
        val section: Section = mock()
        whenever(dataController.trySetSection(section)).thenReturn(true)
        whenever(dataController.currentPosition).thenReturn(23)

        assertTrue(presenter.trySetSection(section))
        verify(view).setCurrentPosition(23)
    }

    @Test
    fun testTrySetSectionFalse() {
        val section: Section = mock()
        whenever(dataController.trySetSection(any())).thenReturn(false)

        assertFalse(presenter.trySetSection(section))
        verify(view, never()).setCurrentPosition(any())
    }

}