package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.interfaces.StorageLocation


class StorageLocationConverter {
    @TypeConverter
    fun toString(storageLocation: StorageLocation): String {
        return storageLocation.name
    }
    @TypeConverter
    fun toStorageLocationEnum(value: String): StorageLocation {
        return StorageLocation.valueOf(value)
    }
}
