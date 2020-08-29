package de.taz.app.android.singletons

import de.taz.app.android.annotation.Mockable
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


enum class DateFormat {
    LongWithWeekDay,
    LongWithoutWeekDay
}

enum class AppTimeZone {
    Default,
    Berlin
}

@Mockable
object DateHelper {

    val now
        get() = Date().time

    private val dateHelper = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val cal: Calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
    private val calDefaultTimeZone = Calendar.getInstance()
    // if we want to use devices Locale replace Locale.GERMAN with:
    // ConfigurationCompat.getLocales(applicationContext.resources.configuration)[0]
    private val deviceLocale = Locale.GERMAN

    fun today(timeZone: AppTimeZone): Long {
        return when (timeZone) {
            AppTimeZone.Default -> calDefaultTimeZone.timeInMillis
            AppTimeZone.Berlin -> cal.timeInMillis
        }
    }

    private fun dateToString(date: Date) : String {
        return dateHelper.format(date)
    }

    fun longToString(time: Long): String {
        val date = Date(time)
        return dateHelper.format(date)
    }


    fun stringToDate(string: String): Date? {
        return dateHelper.parse(string)
    }

    fun stringToDateWithDelta(string: String, days: Int): Date? {
        return stringToDate(string)?.let { date ->
            cal.time = date
            cal.add(Calendar.DAY_OF_YEAR, days)
            cal.time
        }
    }

    fun stringToLong(string: String): Long? {
        return dateHelper.parse(string)?.time
    }

    fun stringToStringWithDelta(string: String, days: Int): String? {
        return stringToDateWithDelta(string, days)?.let {
            dateToString(it)
        }
    }

    fun stringToLongLocalizedString(dateString: String): String? {
        return SimpleDateFormat("yyyy-MM-dd", deviceLocale).parse(dateString)?.let { issueDate ->
            SimpleDateFormat("EEEE, d.M.yyyy", deviceLocale).format(
                issueDate
            ).toLowerCase(Locale.getDefault())
        }
    }

    fun stringToMediumLocalizedString(dateString: String): String? {
        return SimpleDateFormat("yyyy-MM-dd", deviceLocale).parse(dateString)?.let { issueDate ->
            SimpleDateFormat("d.M.yyyy", deviceLocale).format(
                issueDate
            ).toLowerCase(Locale.getDefault())
        }
    }

    fun dateToLowerCaseString(date: String): String? {
        return SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).parse(date)?.let { issueDate ->
            SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.GERMANY).format(
                issueDate
            ).toLowerCase(Locale.getDefault())
        }
    }

    fun dayDelta(earlierDate: String, laterDate: String) : Long {
        return TimeUnit.MILLISECONDS.toDays(stringToDate(laterDate)!!.time - stringToDate(earlierDate)!!.time)
    }
}