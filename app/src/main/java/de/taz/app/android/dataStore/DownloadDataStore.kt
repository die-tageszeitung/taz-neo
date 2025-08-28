package de.taz.app.android.dataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.BuildConfig
import de.taz.app.android.util.SingletonHolder

// region old setting names
private const val PREFERENCES_DOWNLOADS = "preferences_downloads"
// endregion

// region setting keys
private const val ONLY_WIFI = "download_only_wifi"
private const val ENABLED = "download_enabled"
private const val PDF_ADDITIONALLY = "download_pdf_additionally"
private const val NOTIFICATIONS_ENABLED = "notifications_enabled"
private const val PDF_DIALOG_DO_NOT_SHOW_AGAIN = "download_pdf_dialog_do_not_show_again"
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

    val pdfAdditionally = SimpleDataStoreEntry(dataStore, booleanPreferencesKey(PDF_ADDITIONALLY), BuildConfig.IS_LOADING_PDF_ADDITIONALLY)

    val notificationsEnabled = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(
            NOTIFICATIONS_ENABLED
        ), true
    )

    val pdfDialogDoNotShowAgain = SimpleDataStoreEntry(dataStore, booleanPreferencesKey(PDF_DIALOG_DO_NOT_SHOW_AGAIN), false)

    val onlyWifi = SimpleDataStoreEntry(dataStore, booleanPreferencesKey(ONLY_WIFI), true)
}
