package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.dto.StorageType


class StorageTypeConverter {
    @TypeConverter
    fun toString(storageType: StorageType): String {
        return storageType.name
    }

    @TypeConverter
    fun toStorageType(value: String): StorageType {
        return StorageType.valueOf(value)
    }

}