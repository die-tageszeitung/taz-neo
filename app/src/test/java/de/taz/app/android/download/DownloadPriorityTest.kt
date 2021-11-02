package de.taz.app.android.download

import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPriorityTest {

    @Test
    fun downloadPriorityIsComparable() {
        assertTrue(DownloadPriority.High > DownloadPriority.Normal)
    }
}