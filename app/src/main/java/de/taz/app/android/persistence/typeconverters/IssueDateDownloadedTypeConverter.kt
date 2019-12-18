package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import java.text.SimpleDateFormat
import java.util.*


class IssueDateDownloadTypeConverter {
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @TypeConverter
    fun toString(issueDateDownload: Date?): String {
        issueDateDownload?.let {
            return simpleDateFormat.format(issueDateDownload)
        }
        return ""
    }

    @TypeConverter
    fun toIssueDateDownload(value: String): Date? {
        if (value == "") {
            return null
        }
        return simpleDateFormat.parse(value) ?: null
    }
}
