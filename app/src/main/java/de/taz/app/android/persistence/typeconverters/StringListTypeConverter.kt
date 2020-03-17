package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import de.taz.app.android.singletons.JsonHelper

class StringListTypeConverter {

    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val adapter = JsonHelper.moshi.adapter<List<String>>(stringListType)

    @TypeConverter
    fun toString(stringList: List<String>): String {
        return adapter.toJson(stringList)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return adapter.fromJson(value) ?: listOf()
    }
}