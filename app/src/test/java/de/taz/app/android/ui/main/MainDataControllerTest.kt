package de.taz.app.android.ui.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import de.taz.app.android.TestLifecycleOwner
import de.taz.app.android.testIssueStub
import org.junit.Test

import org.junit.Assert.*
import org.junit.Rule

class MainDataControllerTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun observeIssue() {
        val mainDataController = MainDataController()
        val testLifecycleOwner = TestLifecycleOwner()

        var called = false
        mainDataController.observeIssueStub(testLifecycleOwner) {
            called = true
        }
        mainDataController.setIssueStub(testIssueStub)

        assertTrue(called)
    }

    @Test
    fun getIssue() {
        val mainDataController = MainDataController()

        assertEquals(null, mainDataController.getIssueStub())

        mainDataController.setIssueStub(testIssueStub)

        assertEquals(testIssueStub, mainDataController.getIssueStub())
    }

}