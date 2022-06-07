package de.taz.app.android.dataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.BuildConfig
import de.taz.app.android.util.SingletonHolder

// region old setting names
private const val PREFERENCES_GENERAL = "preferences_general"
private const val PREFERENCES_TAZ_API_CSS = "preferences_tazapicss"
// endregion

// region setting keys
private const val DATA_POLICY_ACCEPTED = "data_policy_accepted"
private const val FIRST_APP_START = "first_time_app_starts"
private const val DRAWER_SHOWN_COUNT = "DRAWER_SHOWN_NUMBER"
private const val PDF_MODE = "pdf_mode"
private const val TRY_PDF_DIALOG_COUNT = "try_pdf_shown"

// endregion

private val Context.generalDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_GENERAL,
    produceMigrations = {
        listOf(
            SharedPreferencesMigration(
                it,
                PREFERENCES_TAZ_API_CSS,
                keysToMigrate = setOf(
                    DATA_POLICY_ACCEPTED, FIRST_APP_START, DRAWER_SHOWN_COUNT, PDF_MODE, TRY_PDF_DIALOG_COUNT
                )
            ),
        )
    }
)

class GeneralDataStore private constructor(applicationContext: Context) {

    private val dataStore = applicationContext.generalDataStore

    companion object : SingletonHolder<GeneralDataStore, Context>(::GeneralDataStore)

    val dataPolicyAccepted: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(DATA_POLICY_ACCEPTED), false
    )

    val hasSeenWelcomeScreen: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(FIRST_APP_START), false
    )

    val drawerShownCount: DataStoreEntry<Int> = SimpleDataStoreEntry(
        dataStore, intPreferencesKey(DRAWER_SHOWN_COUNT), 0
    )

    val pdfMode: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(PDF_MODE), BuildConfig.IS_PDF_MODE_DEFAULT
    )

    val tryPdfDialogCount: DataStoreEntry<Int> = SimpleDataStoreEntry(
        dataStore, intPreferencesKey(TRY_PDF_DIALOG_COUNT), 0
    )

}
