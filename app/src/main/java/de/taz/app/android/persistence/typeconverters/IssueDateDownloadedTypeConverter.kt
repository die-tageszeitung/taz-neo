package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import java.text.SimpleDateFormat
import java.util.*


class IssueDateDownloadTypeConverter {
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @TypeConverter
    fun toString(issueDateDownload: Date): String {
        return simpleDateFormat.format(issueDateDownload)
    }

    @TypeConverter
    fun toIssueDateDownload(value: String): Date? {
        return simpleDateFormat.parse(value) ?: Date()
    }
}
