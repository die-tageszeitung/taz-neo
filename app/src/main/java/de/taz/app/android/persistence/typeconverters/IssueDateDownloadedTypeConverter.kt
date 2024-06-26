package de.taz.app.android.persistence.typeconverters


import androidx.room.TypeConverter
import de.taz.app.android.util.Log
import de.taz.app.android.sentry.SentryWrapper
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

private const val FORMAT_STRING = "yyyy-MM-dd HH:mm:ss"

class IssueDateDownloadTypeConverter {
    private val log by Log

    @TypeConverter
    fun toString(issueDateDownload: Date?): String? {
        val simpleDateFormat = SimpleDateFormat(FORMAT_STRING, Locale.US)

        issueDateDownload?.let {
            return simpleDateFormat.format(issueDateDownload)
        }
        return null
    }

    @TypeConverter
    fun toIssueDateDownload(value: String?): Date? {
        val simpleDateFormat = SimpleDateFormat(FORMAT_STRING, Locale.US)

        if (value == "" || value == "null" || value == null) {
            return null
        }
        return try {
            simpleDateFormat.parse(value)
        } catch (e: ParseException) {
            log.error("Problems parsing date as $FORMAT_STRING. ${e.message}")
            SentryWrapper.captureException(e)
            return null
        }
    }
}
