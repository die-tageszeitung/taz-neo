package de.taz.app.android.util

import android.content.Context
import android.text.format.DateFormat
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.*

class DateHelper private constructor(applicationContext: Context): ViewModel() {

    companion object : SingletonHolder<DateHelper, Context>(::DateHelper)

    private val dateHelper = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val cal: Calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
    private val localDateFormat = DateFormat.getMediumDateFormat(applicationContext)

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

    fun stringToLocalizedString(dateString: String): String? {
        return stringToDate(dateString)?.let { localDateFormat.format(it) }
    }

    fun dateToLowerCaseString(date: String): String? {
        return SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).parse(date)?.let { issueDate ->
            SimpleDateFormat("EEEE, dd. MMMM yyyy", Locale.GERMANY).format(
                issueDate
            ).toLowerCase(Locale.getDefault())
        }
    }

}