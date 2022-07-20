package de.taz.app.android.singletons

import de.taz.app.android.annotation.Mockable
import java.text.SimpleDateFormat
import java.util.*


enum class DateFormat {
    None,
    LongWithWeekDay,
    LongWithoutWeekDay
}

enum class AppTimeZone {
    Default,
    Berlin
}

@Mockable
object DateHelper {

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

    private fun dateToString(date: Date): String {
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

    fun dateToLongLocalizedString(date: Date): String {
        return SimpleDateFormat("EEEE, d.M.yyyy", deviceLocale).format(
            date
        ).toLowerCase(Locale.getDefault())
    }

    fun stringToLongLocalizedString(dateString: String): String? {
        return SimpleDateFormat("yyyy-MM-dd", deviceLocale).parse(dateString)?.let { issueDate ->
            dateToLongLocalizedString(issueDate)
        }
    }

    fun dateToMediumLocalizedString(date: Date): String {
        return SimpleDateFormat("d.M.yyyy", deviceLocale).format(
            date
        ).toLowerCase(Locale.getDefault())
    }

    fun dateToWeekendNotation(date: Date): String {
        return SimpleDateFormat("d. MMMM yyyy", Locale.GERMANY).format(date).toLowerCase(Locale.GERMANY)
    }

    /**
     * function to get a two lines string from a given dateString
     * @param dateString - String holding the date which will be reformatted
     * @return the [String] of date in a two line format: "EEEE,<br>> d.M.yyyy", eg:
     *    samstag,
     *    13.3.2021
     */
    fun stringToLongLocalized2LineString(dateString: String): String? {
        return SimpleDateFormat("yyyy-MM-dd", deviceLocale).parse(dateString)?.let { issueDate ->
            SimpleDateFormat("EEEE,\n d.M.yyyy", deviceLocale).format(
                issueDate
            ).toLowerCase(Locale.getDefault())
        }
    }

    fun stringToMediumLocalizedString(dateString: String): String? {
        return SimpleDateFormat("yyyy-MM-dd", deviceLocale).parse(dateString)?.let { issueDate ->
            dateToMediumLocalizedString(issueDate)
        }
    }

    fun dateToLowerCaseString(date: Date): String {
        return SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.GERMANY).format(
            date
        ).toLowerCase(Locale.getDefault())
    }

    fun dateToLowerCaseString(date: String): String? {
        return SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).parse(date)?.let { issueDate ->
            return dateToLowerCaseString(issueDate)
        }
    }

    fun dateToMonthYearString(date: Date): String {
        return SimpleDateFormat("MM/yyyy", Locale.GERMANY).format(
            date
        )
    }

    fun sameDays(date: Date, other: Date): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = date
        cal2.time = other
        return cal1[Calendar.DAY_OF_YEAR] == cal2[Calendar.DAY_OF_YEAR] &&
                cal1[Calendar.YEAR] == cal2[Calendar.YEAR]
    }

    fun subDays(date: Date, days: Int): Date {
        val calenderItem = Calendar.getInstance().apply {
            time = date
            add(Calendar.DATE, -days)
        }
        return Date(calenderItem.time.time)
    }

    fun addDays(date: Date, days: Int): Date {
        val calenderItem = Calendar.getInstance().apply {
            time = date
            add(Calendar.DATE, days)
        }
        return Date(calenderItem.time.time)
    }

    fun yesterday(): Date {
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return cal.time
    }

    fun lastWeek(): Date {
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        return cal.time
    }

    fun lastMonth(): Date {
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -31)
        return cal.time
    }

    fun lastYear(): Date {
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -365)
        return cal.time
    }
}