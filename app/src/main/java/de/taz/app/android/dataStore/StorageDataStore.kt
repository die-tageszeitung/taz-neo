package de.taz.app.android.dataStore

import android.content.Context
import android.os.StatFs
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.SingletonHolder

// region new name
private const val PREFERENCES_STORAGE = "preferences_storage"
//endregion

// region old setting names
private const val PREFERENCES_GENERAL = "preferences_general"
// endregion

// region setting keys
const val KEEP_ISSUES_NUMBER = "general_keep_number_issues"
const val STORAGE_LOCATION = "general_storage_location"
// endregion

// region defaults
private const val KEEP_ISSUES_DEFAULT = 20
private val STORAGE_LOCATION_DEFAULT = StorageLocation.INTERNAL
// endregion

private val Context.storageDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_STORAGE,
    produceMigrations = {
        listOf(
            SharedPreferencesMigration(
                it,
                PREFERENCES_GENERAL,
                keysToMigrate = setOf(
                    KEEP_ISSUES_NUMBER, STORAGE_LOCATION
                )
            ),
        )
    }
)

class StorageDataStore private constructor(applicationContext: Context) {

    private val dataStore = applicationContext.storageDataStore

    companion object : SingletonHolder<StorageDataStore, Context>(::StorageDataStore)

    private val storageService = StorageService.getInstance(applicationContext)

    val keepIssuesNumber = MappingDataStoreEntry(
        dataStore,
        stringPreferencesKey(KEEP_ISSUES_NUMBER),
        KEEP_ISSUES_DEFAULT,
        { it.toString() },
        { it.toInt() }
    )

    val storageLocation = MappingDataStoreEntry(
        dataStore,
        intPreferencesKey(STORAGE_LOCATION),
        STORAGE_LOCATION_DEFAULT,
        { it.ordinal },
        { StorageLocation.values()[it] },
        ::determineStorageLocationBySize
    )

    private suspend fun determineStorageLocationBySize(): StorageLocation {
        val externalFreeBytes =
            storageService.getExternalFilesDir()?.let { StatFs(it.path).availableBytes }
        val internalFreeBytes = StatFs(storageService.getInternalFilesDir().path).availableBytes

        val selectedStorageLocation =
            if (externalFreeBytes != null && externalFreeBytes > internalFreeBytes && storageService.externalStorageAvailable()) {
                StorageLocation.EXTERNAL
            } else {
                StorageLocation.INTERNAL
            }

        storageLocation.set(selectedStorageLocation)
        return selectedStorageLocation
    }

}
