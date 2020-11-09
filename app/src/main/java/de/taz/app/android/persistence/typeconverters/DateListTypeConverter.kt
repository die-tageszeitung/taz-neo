package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import com.squareup.moshi.Types
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.JsonHelper
import java.util.*

class DateListTypeConverter {
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val adapter = JsonHelper.moshi.adapter<List<String>>(stringListType)

    @TypeConverter
    fun toString(dateList: List<Date>): String {
        val datesAsString = dateList.sortedDescending().map(simpleDateFormat::format)
        return adapter.toJson(datesAsString)
    }

    @TypeConverter
    fun toDateList(value: String): List<Date> {
        return adapter.fromJson(value)?.map(simpleDateFormat::parse)?.sortedDescending() ?: emptyList()
    }
}