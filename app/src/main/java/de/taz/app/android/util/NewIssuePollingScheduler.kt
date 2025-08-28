package de.taz.app.android.util
import java.util.Calendar

object NewIssuePollingScheduler {
    private val checkHours = listOf(5, 18, 21).sorted()

    private fun determineNextScheduledHour(from: Int): Int {
        var candidate: Int = checkHours.first()
        for (n in checkHours) {
            if (n > from) {
                candidate = n
                break
            }
        }
        return candidate
    }

    fun getDateNextPoll(fromDate: Calendar): Calendar {

        val dueDate = Calendar.getInstance()
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)

        val currentHour = fromDate.get(Calendar.HOUR_OF_DAY)
        val nextCheckHour = determineNextScheduledHour(currentHour)

        dueDate.set(Calendar.HOUR_OF_DAY, nextCheckHour)

        // If due date is suddenly smaller than current date it means it need to be the next day
        if (dueDate < fromDate) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }

        return dueDate
    }

    fun getDelayForNextPoll(fromDate: Calendar = Calendar.getInstance()): Long {
        return getDateNextPoll(fromDate).timeInMillis - fromDate.timeInMillis
    }
}