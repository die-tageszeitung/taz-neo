package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class StringListTypeConverter {

    private val moshi = Moshi.Builder().build()
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val adapter = moshi.adapter<List<String>>(stringListType)

    @TypeConverter
    fun toString(stringList: List<String>): String {
        return adapter.toJson(stringList)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return adapter.fromJson(value) ?: listOf()
    }
}