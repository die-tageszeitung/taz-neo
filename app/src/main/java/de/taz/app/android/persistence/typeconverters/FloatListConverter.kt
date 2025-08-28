package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.util.Json

class FloatListConverter {
    @TypeConverter
    fun toString(floatList: List<Float>?): String? {
        return floatList?.let { Json.encodeToString(floatList) }
    }

    @TypeConverter
    fun toFloatList(value: String?): List<Float>? {
        return value?.let { Json.decodeFromString(value) }
    }
}