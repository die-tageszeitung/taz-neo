package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import java.text.SimpleDateFormat
import java.util.*


class IssueDateDownloadedTypeConverter {
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @TypeConverter
    fun toString(issueDateDownloaded: Date): String {
        return simpleDateFormat.format(issueDateDownloaded)
    }

    @TypeConverter
    fun toIssueDateDownloaded(value: String): Date {
        return simpleDateFormat.parse(value) ?: Date()
    }
}
