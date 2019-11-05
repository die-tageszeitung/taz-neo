package de.taz.app.android.util

import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.*

object DateHelper {

    private val dateHelper = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))

    fun dateToString(date: Date) : String {
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

    fun stringToStringWithDelta(string: String, days: Int): String? {
        return stringToDateWithDelta(string, days)?.let {
            dateToString(it)
        }
    }

}