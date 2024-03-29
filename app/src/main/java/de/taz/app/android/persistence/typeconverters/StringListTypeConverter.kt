package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import de.taz.app.android.util.Json

class StringListTypeConverter {

    @TypeConverter
    fun toString(stringList: List<String>): String {
        return Json.encodeToString(stringList)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return Json.decodeFromString(value)
    }
}