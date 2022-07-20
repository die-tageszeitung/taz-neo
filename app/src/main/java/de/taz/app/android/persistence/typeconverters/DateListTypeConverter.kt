package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.simpleDateFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import de.taz.app.android.util.Json
import java.util.*

class DateListTypeConverter {

    @TypeConverter
    fun toString(dateList: List<Date>): String {
        val datesAsString = dateList.sortedDescending().map(simpleDateFormat::format)
        return Json.encodeToString(datesAsString)
    }

    @TypeConverter
    fun toDateList(value: String): List<Date> {
        return Json.decodeFromString<List<String>>(value).map(simpleDateFormat::parse)
            .sortedDescending()
    }
}