package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import java.util.*

class UUIDTypeConverter {

    @TypeConverter
    fun toString(uuid: UUID?): String? {
        return uuid?.toString() ?: ""
    }

    @TypeConverter
    fun toStringList(value: String?): UUID? {
        return if (value.isNullOrEmpty()) null else UUID.fromString(value)
    }
}