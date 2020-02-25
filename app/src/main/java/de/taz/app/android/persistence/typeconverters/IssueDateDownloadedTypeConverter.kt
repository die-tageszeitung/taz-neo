package de.taz.app.android.persistence.typeconverters


import androidx.room.TypeConverter
import de.taz.app.android.util.Log
import io.sentry.Sentry
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


class IssueDateDownloadTypeConverter {
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val FORMAT_STRING = "HH:mm:ss.SSSZ"
    private val log by Log

    @TypeConverter
    fun toString(issueDateDownload: Date?): String {
        val simpleDateFormat = SimpleDateFormat(FORMAT_STRING, Locale.US)

        issueDateDownload?.let {
            return simpleDateFormat.format(issueDateDownload)
        }
        return ""
    }

    @TypeConverter
    fun toIssueDateDownload(value: String): Date? {
        val simpleDateFormat = SimpleDateFormat(FORMAT_STRING, Locale.US)

        if (value == "") {
            return null
        }
        return try {
            simpleDateFormat.parse(value)
        } catch (e: ParseException) {
            log.error("SHIT HAPPENED: ${e.message}")
            Sentry.capture(e)
            return null
        }
    }
}
