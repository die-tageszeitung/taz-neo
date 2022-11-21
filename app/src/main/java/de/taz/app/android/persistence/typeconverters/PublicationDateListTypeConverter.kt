package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.models.PublicationDate
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.util.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString


/**
 * Convert a list of [PublicationDate]s to json by using arrays.
 * To reduce the required storage space each [PublicationDate] is converted to an array with one
 * or two elements. The first element will contain the [PublicationDate.date] and the second
 * [PublicationDate.validity] if it is not null.
 * This converter is lenient and will omit elements that don't have at least a valid date in
 * the first array index.
 */
class PublicationDateListTypeConverter {
    @TypeConverter
    fun toString(publicationDateList: List<PublicationDate>): String {
        val arrayList = publicationDateList.map {
            if (it.validity != null) {
                arrayListOf(
                    simpleDateFormat.format(it.date),
                    simpleDateFormat.format(it.validity)
                )
            } else {
                arrayListOf(simpleDateFormat.format(it.date))
            }
        }
        return Json.encodeToString(arrayList)
    }

    @TypeConverter
    fun toDateList(value: String): List<PublicationDate> {
        val arrayList = Json.decodeFromString<List<List<String>>>(value)
        return arrayList.mapNotNull {
            val date = it.firstOrNull()?.let(simpleDateFormat::parse)
            val validity = it.getOrNull(1)?.let(simpleDateFormat::parse)

            date?.let {
                PublicationDate(date, validity)
            }
        }
    }
}