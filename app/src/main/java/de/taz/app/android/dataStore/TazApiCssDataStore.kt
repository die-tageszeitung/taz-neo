package de.taz.app.android.dataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.R
import de.taz.app.android.util.SingletonHolder

// region old setting names
private const val PREFERENCES_TAZ_API_CSS = "preferences_tazapicss"
// endregion

// region setting keys
private const val FONT_SIZE = "text_font_size"
private const val TEXT_JUSTIFICATION = "text_justification"
private const val NIGHT_MODE = "text_night_mode"
private const val MULTI_COLUMN_MODE = "text_multi_column_mode"
private const val TAP_TO_SCROLL = "tap_to_scroll"
private const val KEEP_SCREEN_ON = "keep_screen_on"
private const val LOGO_WIDTH = "logo_width"
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

    val textJustification: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(TEXT_JUSTIFICATION), false
    )

    val nightMode: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(NIGHT_MODE), false
    )

    val multiColumnMode: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(MULTI_COLUMN_MODE), false
    )

    val tapToScroll: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(TAP_TO_SCROLL), false
    )

    val keepScreenOn: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(KEEP_SCREEN_ON), false
    )

    val logoWidth: DataStoreEntry<Int> = SimpleDataStoreEntry(
        dataStore, intPreferencesKey(LOGO_WIDTH), -1
    )

}
