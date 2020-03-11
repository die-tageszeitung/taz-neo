package de.taz.app.android.ui.archive.item

import com.nhaarman.mockitokotlin2.*
import de.taz.app.android.TestLifecycleOwner
import de.taz.app.android.ui.moment.MomentViewContract
import de.taz.app.android.ui.moment.MomentViewModel
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@kotlinx.coroutines.ExperimentalCoroutinesApi
class ArchiveItemPresenterTest {


    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @Mock
    lateinit var momentViewContractView: MomentViewContract.View
    @Mock
    lateinit var issueRepository: IssueRepository

    private val lifecycleOwner = TestLifecycleOwner()

    private lateinit var model: MomentViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        MockitoAnnotations.initMocks(this)

        doReturn(lifecycleOwner).`when`(momentViewContractView).getLifecycleOwner()
        model = MomentViewModel(issueRepository)

        model.attach(momentViewContractView)
        model.onViewCreated(null)
    }

    @Test
    fun clearIssue() {
        model.clearIssue()
        verify(momentViewContractView, times(1)).clearIssue()
    }
}