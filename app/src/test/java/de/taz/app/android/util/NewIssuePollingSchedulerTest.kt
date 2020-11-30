package de.taz.app.android.util

import org.junit.Assert
import org.junit.Test
import java.util.*

class NewIssuePollingSchedulerTest {
    @Test
    fun `getNextFromNoonIsEvening`() {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 12)
        now.set(Calendar.MINUTE, 0)
        val nextDate = NewIssuePollingScheduler.getDateNextPoll(now)
        Assert.assertEquals(18, nextDate.get(Calendar.HOUR_OF_DAY))
        Assert.assertEquals(now.get(Calendar.DAY_OF_MONTH), nextDate.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `checkAgainOnLateNight`() {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 18)
        now.set(Calendar.MINUTE, 6)
        val nextDate = NewIssuePollingScheduler.getDateNextPoll(now)
        Assert.assertEquals(21, nextDate.get(Calendar.HOUR_OF_DAY))
        Assert.assertEquals(now.get(Calendar.DAY_OF_MONTH), nextDate.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `afterLateNightCheckInTheMorning`() {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 21)
        now.set(Calendar.MINUTE, 0)
        val nextDate = NewIssuePollingScheduler.getDateNextPoll(now)
        Assert.assertEquals(5, nextDate.get(Calendar.HOUR_OF_DAY))
        // should be next day then
        Assert.assertNotEquals(now.get(Calendar.DAY_OF_MONTH), nextDate.get(Calendar.DAY_OF_MONTH))
        // but a maximum of 8 hours difference
        Assert.assertTrue(nextDate.timeInMillis - now.timeInMillis <= 28800000)
    }

    @Test
    fun `getNextFromMorningIsMorning`() {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 4)
        now.set(Calendar.MINUTE, 0)
        val nextDate = NewIssuePollingScheduler.getDateNextPoll(now)
        Assert.assertEquals(5, nextDate.get(Calendar.HOUR_OF_DAY))
        Assert.assertEquals(now.get(Calendar.DAY_OF_MONTH), nextDate.get(Calendar.DAY_OF_MONTH))
    }
}