package de.taz.app.android.singletons

import de.taz.app.android.annotation.Mockable
import java.text.ParseException
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

    fun longToString(time: Long): String {
        val date = Date(time)
        return dateHelper.format(date)
    }


    fun stringToDate(string: String): Date? {
        return dateHelper.parse(string)
    }

    fun stringToLong(string: String): Long? {
        return dateHelper.parse(string)?.time
    }

    fun dateToLongLocalizedLowercaseString(date: Date): String {
        return SimpleDateFormat("EEEE, d.M.yyyy", deviceLocale).format(
            date
        ).lowercase(Locale.getDefault())
    }

    fun dateToLongLocalizedString(date: Date): String {
        return SimpleDateFormat("EEEE, d.M.yyyy", deviceLocale).format(
            date
        )
    }

    /**
     * returns eg "Dienstag, 23.8.2022"
     */
    fun stringToLongLocalizedString(dateString: String): String? {
        if (dateString == "") return null
        return SimpleDateFormat("yyyy-MM-dd", deviceLocale).parse(dateString)?.let { issueDate ->
            dateToLongLocalizedString(issueDate)
        }
    }

    /**
     * returns eg "dienstag, 23.8.2022"
     */
    fun stringToLongLocalizedLowercaseString(dateString: String): String? {
        if (dateString == "") return null
        return SimpleDateFormat("yyyy-MM-dd", deviceLocale).parse(dateString)?.let { issueDate ->
            dateToLongLocalizedLowercaseString(issueDate)
        }
    }

    fun dateToMediumLocalizedString(date: Date): String {
        return SimpleDateFormat("d.M.yyyy", deviceLocale).format(
            date
        ).lowercase(Locale.getDefault())
    }

    fun dateToWeekendNotation(date: Date): String {
        return SimpleDateFormat("d. MMMM yyyy", Locale.GERMANY).format(date)
            .lowercase(Locale.GERMANY)
    }

    /**
     * function to get the formatted date for the wochentaz
     * @param date - Date of the issue
     * @param validityDate - String (eg "2022-11-18") given by [IssueStub.validityDate]
     * @return eg "woche 12. – 18.11.2022"
     */
    fun dateToWeekNotation(date: Date?, validityDate: String): String {
        val formattedToDate = stringToDate(validityDate)
        val fromDate = date?.let {
            SimpleDateFormat("d.", Locale.GERMANY).format(it)
        }
        val toDate = formattedToDate?.let {
            SimpleDateFormat("d.M.yyyy", Locale.GERMANY).format(it)
        }
        return "woche $fromDate – $toDate"
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
            ).lowercase(Locale.getDefault())
        }
    }

    /**
     * function to get a two lines string from 2 given date strings
     * @param fromDate - String holding the "from" date
     * @param toDate - String holding the "until" date
     * @return the [String] of date in a two line format: "EEEE,<br>> d.M.yyyy", eg:
     *    woche
     *    12. – 18.11.2022
     */
    fun stringsToWeek2LineString(fromDate: String, toDate: String): String? {
        val realFromDate = stringToDate(fromDate)
        val realToDate = stringToDate(toDate)
        val formattedFromDate = realFromDate?.let {
            SimpleDateFormat("d.", Locale.GERMANY).format(it)
        }
        val formattedToDate = realToDate?.let {
            SimpleDateFormat("d.MM.yyyy", Locale.GERMANY).format(it)
        }
        return "woche\n$formattedFromDate – $formattedToDate"
    }

    fun stringToMediumLocalizedString(dateString: String): String? {
        return try {
            SimpleDateFormat("yyyy-MM-dd", deviceLocale).parse(dateString)?.let { issueDate ->
                dateToMediumLocalizedString(issueDate)
            }
        } catch (e: ParseException) {
            null
        }
    }

    fun dateToLowerCaseString(date: Date): String {
        return SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.GERMANY).format(
            date
        ).lowercase(Locale.getDefault())
    }

    fun dateToLowerCaseString(date: String): String? {
        return SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).parse(date)?.let { issueDate ->
            return dateToLowerCaseString(issueDate)
        }
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