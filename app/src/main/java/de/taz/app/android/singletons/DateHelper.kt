package de.taz.app.android.singletons

import de.taz.app.android.annotation.Mockable
import de.taz.app.android.appLocale
import io.ktor.util.date.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


enum class DateFormat {
    None,
    LongWithWeekDay,
    LongWithoutWeekDay,
    MonthNameAndYear,
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
        return try {
            dateHelper.parse(string)
        } catch (e: ParseException) {
            null
        }
    }

    fun stringToLong(string: String): Long? {
        return stringToDate(string)?.time
    }

    fun dateToLongLocalizedLowercaseString(date: Date): String {
        return SimpleDateFormat("EEEE, d.M.yyyy", appLocale)
            .format(date)
            .lowercase(appLocale)
    }

    fun dateToLongLocalizedString(date: Date): String {
        return SimpleDateFormat("EEEE, d.M.yyyy", appLocale).format(date)
    }

    /**
     * returns eg "Dienstag, 23.8.2022"
     */
    fun stringToLongLocalizedString(dateString: String): String? {
        if (dateString == "") return null
        return stringToDate(dateString)?.let { issueDate ->
            dateToLongLocalizedString(issueDate)
        }
    }

    /**
     * returns eg "dienstag, 23.8.2022"
     */
    fun stringToLongLocalizedLowercaseString(dateString: String): String? {
        if (dateString == "") return null
        return stringToDate(dateString)?.let { issueDate ->
            dateToLongLocalizedLowercaseString(issueDate)
        }
    }

    /**
     * function to get the formatted date with month and year
     * @param dateString - string of date in form of "yyyy-MM-dd"
     * @return eg "01/2023"
     */
    fun stringToMonthYearString(dateString: String): String? {
        if (dateString == "") return null
        return SimpleDateFormat("yyyy-MM-dd", appLocale).parse(dateString)?.let { issueDate ->
            dateToMonthYearString(issueDate)
        }
    }

    /**
     * returns eg "Dezember 2023"
     */
    fun dateToLocalizedMonthAndYearString(date: Date): String {
        return SimpleDateFormat("MMMM yyyy", appLocale).format(
            date
        )
    }


    /**
     * Returns e.g. "23.8.2022"
     */
    fun dateToMediumLocalizedString(date: Date): String {
        return SimpleDateFormat("d.M.yyyy", appLocale).format(date)
    }

    /**
     * Returns e.g. "23.8.22"
     */
    fun dateToShortLocalizedString(date: Date): String {
        return SimpleDateFormat("d.M.yy", appLocale).format(date)
    }

    fun dateToWeekendNotation(date: Date): String {
        return SimpleDateFormat("d. MMMM yyyy", appLocale)
            .format(date)
            .lowercase(appLocale)
    }

    /**
     * function to get the formatted date for the wochentaz
     * @param date - Date of the issue
     * @param validityDate - Validity Date of the issue
     * @return eg "woche, 29.1. – 5.2.2023"
     */
    fun dateToWeekNotation(date: Date, validityDate: Date): String {
        val fromDate = SimpleDateFormat("d.M.", appLocale).format(date)
        val toDate = SimpleDateFormat("d.M.yyyy", appLocale).format(validityDate)
        return "woche, $fromDate – $toDate"
    }

    /**
     * function to get the formatted date for the wochentaz
     * @param date - Date of the issue
     * @param validityDate - Validity Date of the issue
     * @return eg "wochentaz, 29.1. – 5.2.2023"
     */
    fun dateToWeekTazNotation(date: Date, validityDate: Date): String {
        val fromDate = SimpleDateFormat("d.M.", appLocale).format(date)
        val toDate = SimpleDateFormat("d.M.yyyy", appLocale).format(validityDate)
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
        return if (date != null && formattedToDate != null) {
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
        return stringToDate(dateString)
            ?.let { issueDate ->
                SimpleDateFormat("EEEE,\n d.M.yyyy", appLocale)
                    .format(issueDate)
                    .lowercase(appLocale)
            }
    }
    /**
     * function to get a two lines string from a given dateString
     * @param dateString - String holding the date which will be reformatted
     * @return the [String] of date in a two line format: "EEEE,<br>> d.M.yyyy", eg:
     *    samstag,
     *    13.3.21
     */
    fun stringToLongLocalized2LineShortString(dateString: String): String? {
        return stringToDate(dateString)
            ?.let { issueDate ->
                SimpleDateFormat("EEEE,\n d.M.yy", appLocale)
                    .format(issueDate)
                    .lowercase(appLocale)
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
            SimpleDateFormat("d.M.", appLocale).format(it)
        }
        val formattedToDate = realToDate?.let {
            SimpleDateFormat("d.M.yyyy", appLocale).format(it)
        }
        return "woche,\n$formattedFromDate – $formattedToDate"
    }
    /**
     * function to get a two lines string from 2 given date strings
     * @param fromDate - String holding the "from" date
     * @param toDate - String holding the "until" date
     * @return the [String] of date in a two line format: "EEEE,<br>> d.M.yyyy", eg:
     *    woche,
     *    29.1. – 5.2.23
     */
    fun stringsToWeek2LineShortString(fromDate: String, toDate: String): String? {
        val realFromDate = stringToDate(fromDate)
        val realToDate = stringToDate(toDate)
        val formattedFromDate = realFromDate?.let {
            SimpleDateFormat("d.M.", appLocale).format(it)
        }
        val formattedToDate = realToDate?.let {
            SimpleDateFormat("d.M.yy", appLocale).format(it)
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
                SimpleDateFormat("d.M.", appLocale).format(fromDate)
            } else {
                SimpleDateFormat("d.", appLocale).format(fromDate)
            }

        val toString = SimpleDateFormat("d.M.yyyy", appLocale).format(toDate)
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
                SimpleDateFormat("d.M.", appLocale).format(fromDate)
            } else {
                SimpleDateFormat("d.", appLocale).format(fromDate)
            }

        val toString = SimpleDateFormat("d.M.yy", appLocale).format(toDate)
        return "$fromString - $toString"
    }

    fun stringToMediumLocalizedString(dateString: String): String? {
        return stringToDate(dateString)?.let { issueDate ->
            dateToMediumLocalizedString(issueDate)
        }
    }

    /**
     * Parse [dateString] and returns a date string in format of "Dezember 2023".
     *
     * @param dateString A parsable date string, like "Jan 12 00:00:00 GMT+01:00 2023".
     * @return localized month name and year, eg "Dezember 2023"
     */
    fun stringToLocalizedMonthAndYearString(dateString: String): String? {
        if (dateString.isBlank()) return null
        return stringToDate(dateString)?.let { date -> dateToLocalizedMonthAndYearString(date) }
    }

    fun dateToLowerCaseString(date: Date): String {
        return SimpleDateFormat("EEEE, d. MMMM yyyy", appLocale)
            .format(date)
            .lowercase(appLocale)
    }

    fun dateToLowerCaseString(dateString: String): String? {
        return stringToDate(dateString)?.let { issueDate ->
            return dateToLowerCaseString(issueDate)
        }
    }

    fun dateToMonthYearString(date: Date): String {
        return SimpleDateFormat("MM/yyyy", Locale.GERMANY).format(
            date
        )
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