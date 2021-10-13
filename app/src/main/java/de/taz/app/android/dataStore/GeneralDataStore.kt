package de.taz.app.android.dataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.util.SingletonHolder

// region old setting names
// TODO this should be here - remove from [Constants]
// private const val PREFERENCES_GENERAL = "preferences_general"
private const val PREFERENCES_TAZ_API_CSS = "preferences_tazapicss"
// endregion

// region setting keys
private const val DATA_POLICY_ACCEPTED = "data_policy_accepted"
private const val FIRST_APP_START = "first_time_app_starts"
// endregion

private val Context.generalDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_GENERAL,
    produceMigrations = {
        listOf(
            SharedPreferencesMigration(
                it,
                PREFERENCES_TAZ_API_CSS,
                keysToMigrate = setOf(
                    DATA_POLICY_ACCEPTED, FIRST_APP_START
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
}
