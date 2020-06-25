package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.models.DownloadStatus


class DownloadStatusTypeConverter {
    @TypeConverter
    fun toString(downloadStatus: DownloadStatus?): String? {
        return downloadStatus?.name
    }
    @TypeConverter
    fun toDownloadStatusEnum(value: String?): DownloadStatus? {
        return value?.let { DownloadStatus.valueOf(value) }
    }
}