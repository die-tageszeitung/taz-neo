package de.taz.app.android.util

import android.content.Context
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.StorageLocation


fun Context.getStorageLocationCaption(storageLocation: StorageLocation): String {
    return when (storageLocation) {
        StorageLocation.INTERNAL -> getString(R.string.settings_storage_type_internal)
        StorageLocation.EXTERNAL -> getString(R.string.settings_storage_type_external)
        else -> "invalid"
    }
}