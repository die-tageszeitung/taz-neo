package de.taz.app.android.ui.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import de.taz.app.android.TestLifecycleOwner
import de.taz.app.android.testIssue
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
        mainDataController.observeIssue(testLifecycleOwner) {
            called = true
        }
        mainDataController.setIssue(testIssue)

        assertTrue(called)
    }

    @Test
    fun getIssue() {
        val mainDataController = MainDataController()

        assertEquals(null, mainDataController.getIssue())

        mainDataController.setIssue(testIssue)

        assertEquals(testIssue, mainDataController.getIssue())
    }

}