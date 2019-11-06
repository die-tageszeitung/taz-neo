package de.taz.app.android.api.models

import de.taz.app.android.IssueTestUtil
import org.junit.Test

import org.junit.Assert.*

class IssueTest {

    private val issue = IssueTestUtil.getIssue()

    @Test
    fun getAllFiles() {
        val fileList = issue.getAllFiles()

        issue.moment.imageList.forEach { fileEntry ->
            assertTrue(fileList.filter { it == fileEntry }.size == 1)
        }
    }
}