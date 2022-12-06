package de.taz.app.android.singletons

import de.taz.app.android.annotation.Mockable
import io.ktor.util.date.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

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


    /**
     * Returns e.g. "23.8.2022"
     */
    fun dateToMediumLocalizedString(date: Date): String {
        return SimpleDateFormat("d.M.yyyy", deviceLocale).format(
            date
        ).lowercase(Locale.getDefault())
    }

    /**
     * Returns e.g. "23.8.22"
     */
    fun dateToShortLocalizedString(date: Date): String {
        return SimpleDateFormat("d.M.yy", deviceLocale).format(
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
     * @param validityDate - Validity Date of the issue
     * @return eg "woche, 29.1. – 5.2.2023"
     */
    fun dateToWeekNotation(date: Date, validityDate: Date): String {
        val fromDate = SimpleDateFormat("d.M.", Locale.GERMANY).format(date)
        val toDate = SimpleDateFormat("d.M.yyyy", Locale.GERMANY).format(validityDate)
        return "woche, $fromDate – $toDate"
    }

    /**
     * function to get the formatted date for the wochentaz
     * @param date - Date of the issue
     * @param validityDate - Validity Date of the issue
     * @return eg "wochentaz, 29.1. – 5.2.2023"
     */
    fun dateToWeekTazNotation(date: Date, validityDate: Date): String {
        val fromDate = SimpleDateFormat("d.M.", Locale.GERMANY).format(date)
        val toDate = SimpleDateFormat("d.M.yyyy", Locale.GERMANY).format(validityDate)
        return "wochentaz, $fromDate – $toDate"
    }


    /**
     * function to get the formatted date for the wochentaz
     * @param date - Date of the issue
     * @param validityDate - String (eg "2023-02-05") given by [IssueStub.validityDate]
     * @return eg "woche, 29.1. – 5.2.2023"
     */
    fun dateToWeekNotation(date: Date?, validityDate: String): String {
        val formattedToDate = stringToDate(validityDate)
        return if (date !=null && formattedToDate != null ) {
            dateToWeekNotation(date, formattedToDate)
        } else {
            ""
        }
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
     *    woche,
     *    29.1. – 5.2.2023
     */
    fun stringsToWeek2LineString(fromDate: String, toDate: String): String? {
        val realFromDate = stringToDate(fromDate)
        val realToDate = stringToDate(toDate)
        val formattedFromDate = realFromDate?.let {
            SimpleDateFormat("d.M.", Locale.GERMANY).format(it)
        }
        val formattedToDate = realToDate?.let {
            SimpleDateFormat("d.M.yyyy", Locale.GERMANY).format(it)
        }
        return "woche,\n$formattedFromDate – $formattedToDate"
    }

    /**
     * Format a one line range string from the two given dates with medium sized date formats.
     * If the two dates are from the same month, the month is omitted on the from date e.g. 1. - 5.1.2022
     * Otherwise on month changes: 30.12. - 5.1.2022
     */
    fun dateToMediumRangeString(fromDate: Date, toDate: Date): String {
        val fromCalendar = Calendar.getInstance().apply { time = fromDate }
        val toCalendar = Calendar.getInstance().apply { time = toDate }
        val fromString =
            if (fromCalendar.get(Calendar.MONTH) != toCalendar.get(Calendar.MONTH)) {
                SimpleDateFormat("d.M.", deviceLocale).format(fromDate)
            } else {
                SimpleDateFormat("d.", deviceLocale).format(fromDate)
            }

        val toString = SimpleDateFormat("d.M.yyyy", deviceLocale).format(toDate)
        return "$fromString - $toString"
    }

    /**
     * Format a one line range string from the two given dates with short sized date formats.
     * If the two dates are from the same month, the month is omitted on the from date e.g. 1. - 5.1.22
     * Otherwise on month changes: 30.12. - 5.1.22
     */
    fun dateToShortRangeString(fromDate: Date, toDate: Date): String {
        val fromCalendar = Calendar.getInstance().apply { time = fromDate }
        val toCalendar = Calendar.getInstance().apply { time = toDate }
        val fromString =
            if (fromCalendar.get(Calendar.MONTH) != toCalendar.get(Calendar.MONTH)) {
                SimpleDateFormat("d.M.", deviceLocale).format(fromDate)
            } else {
                SimpleDateFormat("d.", deviceLocale).format(fromDate)
            }

        val toString = SimpleDateFormat("d.M.yy", deviceLocale).format(toDate)
        return "$fromString - $toString"
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