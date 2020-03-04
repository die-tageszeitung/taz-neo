package de.taz.app.android.singletons

import android.content.Context
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.ViewModel
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.util.SingletonHolder
import java.text.SimpleDateFormat
import java.util.*

@Mockable
class DateHelper private constructor(applicationContext: Context): ViewModel() {

    companion object : SingletonHolder<DateHelper, Context>(::DateHelper)

    private val dateHelper = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val cal: Calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
    private val deviceLocale = ConfigurationCompat.getLocales(applicationContext.resources.configuration)[0]

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

}