package de.taz.app.android.dataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.util.SingletonHolder

// region old setting names
private const val PREFERENCES_DOWNLOADS = "preferences_downloads"
// endregion

// region setting keys
private const val ONLY_WIFI = "download_only_wifi"
private const val ENABLED = "download_enabled"
// endregion

private val Context.downloadDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_DOWNLOADS,
    produceMigrations = {
        listOf(
            SharedPreferencesMigration(
                it,
                PREFERENCES_DOWNLOADS,
            ),
        )
    }
)

class DownloadDataStore private constructor(applicationContext: Context) {

    private val dataStore = applicationContext.downloadDataStore

    companion object : SingletonHolder<DownloadDataStore, Context>(::DownloadDataStore)

    val enabled = SimpleDataStoreEntry(dataStore, booleanPreferencesKey(ENABLED), true)

    val onlyWifi = SimpleDataStoreEntry(dataStore, booleanPreferencesKey(ONLY_WIFI), true)

}