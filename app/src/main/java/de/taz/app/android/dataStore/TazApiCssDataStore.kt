package de.taz.app.android.dataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.R
import de.taz.app.android.util.SingletonHolder

// region old setting names
private const val PREFERENCES_TAZ_API_CSS = "preferences_tazapicss"
// endregion

// region setting keys
private const val FONT_SIZE = "text_font_size"
private const val NIGHT_MODE = "text_night_mode"
// endregion

private val Context.tazApiCssDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_TAZ_API_CSS,
    produceMigrations = {
        listOf(
            SharedPreferencesMigration(
                it,
                PREFERENCES_TAZ_API_CSS,
                keysToMigrate = setOf(FONT_SIZE, NIGHT_MODE)
            ),
        )
    }
)

class TazApiCssDataStore private constructor(applicationContext: Context) {

    private val dataStore = applicationContext.tazApiCssDataStore

    companion object : SingletonHolder<TazApiCssDataStore, Context>(::TazApiCssDataStore)

    // TODO int is saved as String migrate in the future
    val fontSize: DataStoreEntry<String> = SimpleDataStoreEntry(
        dataStore,
        stringPreferencesKey(FONT_SIZE),
        applicationContext.resources.getInteger(R.integer.text_default_size).toString()
    )

    val nightMode: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(NIGHT_MODE), false
    )

}