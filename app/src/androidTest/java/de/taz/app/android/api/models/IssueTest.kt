package de.taz.app.android.api.models

import de.taz.app.android.IssueTestUtil
import kotlinx.coroutines.runBlocking
import org.junit.Test

import org.junit.Assert.*

class IssueTest {

    private val issue = IssueTestUtil.getIssue()

    @Test
    fun getAllFiles() = runBlocking {
        val fileList = issue.getAllFiles()

        issue.moment.getMomentImage().let{ fileEntry ->
            assertTrue(fileList.filter { it == fileEntry }.size == 1)
        }
    }
}