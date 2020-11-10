package de.taz.app.android.util
import java.util.*

object NewIssuePollingScheduler {
    private val checkHours = listOf(5, 18, 21)

    private fun determineNextScheduledHour(from: Int): Int {
        var candidate: Int = checkHours.first()
        for (n in checkHours) {
            if (n < candidate && n > from) {
                candidate = n
            }
        }
        return candidate
    }

    fun getDelayForNextPoll(): Long {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)

        val currentHour = currentDate.get(Calendar.HOUR_OF_DAY)
        val nextCheckHour = determineNextScheduledHour(currentHour)

        dueDate.set(Calendar.HOUR_OF_DAY, nextCheckHour)

        // If due date is suddenly smaller than current date it means it need to be the next day
        if (dueDate < currentDate) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }


        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        return timeDiff
    }
}