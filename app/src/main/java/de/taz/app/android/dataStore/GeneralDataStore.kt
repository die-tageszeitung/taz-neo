package de.taz.app.android.dataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.BuildConfig
import de.taz.app.android.util.SingletonHolder

// region old setting names
private const val PREFERENCES_GENERAL = "preferences_general"
private const val PREFERENCES_TAZ_API_CSS = "preferences_tazapicss"
// endregion

// region setting keys
private const val DISPLAY_CUTOUT_EXTRA_PADDING = "display_cutout_extra_padding"
private const val PDF_MODE = "pdf_mode"
private const val ALLOW_NOTIFICATIONS_DO_NOT_SHOW_AGAIN = "allow_notifications_do_not_show_again"
private const val ALLOW_NOTIFICATIONS_LAST_TIME_SHOWN = "allow_notifications_last_time_shown"
private const val APP_SESSION_COUNT = "app_session_count"
private const val LAST_MAIN_ACTIVITY_USAGE_TIME = "last_main_activity_usage_time"
private const val DEBUG_SETTINGS_ENABLED = "debug_settings_enabled"
private const val HAS_BEEN_ASKED_FOR_TRACKING_CONSENT = "has_been_asked_for_tracking_consent"
private const val CONSENT_TO_TRACKING = "consent_to_tracking"
// Deprecated/Removed setting keys
private const val ENABLE_EXPERIMENTAL_ARTICLE_READER = "ENABLE_EXPERIMENTAL_ARTICLE_READER"
private const val DATA_POLICY_ACCEPTED = "data_policy_accepted"
private const val TRY_PDF_DIALOG_COUNT = "try_pdf_shown"
private const val DRAWER_SHOWN_COUNT = "DRAWER_SHOWN_NUMBER"
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
                    DATA_POLICY_ACCEPTED, PDF_MODE
                )
            ),
        )
    }
)

class GeneralDataStore private constructor(applicationContext: Context) {

    private val dataStore = applicationContext.generalDataStore

    companion object : SingletonHolder<GeneralDataStore, Context>(::GeneralDataStore)

    val displayCutoutExtraPadding: DataStoreEntry<Int> = SimpleDataStoreEntry(
        dataStore, intPreferencesKey(DISPLAY_CUTOUT_EXTRA_PADDING), 0
    )

    /* The lmd build variant has pdf as its default and only mode.
       That's why the default value for the pdfMode entry depends on whether
       we have the lmd variant or not.
     */
    val pdfMode: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(PDF_MODE), BuildConfig.IS_LMD
    )

    val allowNotificationsDoNotShowAgain: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(ALLOW_NOTIFICATIONS_DO_NOT_SHOW_AGAIN), false
    )

    val allowNotificationsLastTimeShown: DataStoreEntry<String> = SimpleDataStoreEntry(
        dataStore, stringPreferencesKey(ALLOW_NOTIFICATIONS_LAST_TIME_SHOWN), "2022-12-13"
    )

    val appSessionCount: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(APP_SESSION_COUNT), 0L
    )

    val lastMainActivityUsageTimeMs: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(LAST_MAIN_ACTIVITY_USAGE_TIME), 0L
    )

    val debugSettingsEnabled: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(DEBUG_SETTINGS_ENABLED), false
    )

    val hasBeenAskedForTrackingConsent: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(HAS_BEEN_ASKED_FOR_TRACKING_CONSENT), false
    )

    val consentToTracking: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(CONSENT_TO_TRACKING), false
    )

    suspend fun clearRemovedEntries() {
        dataStore.edit {
            // This functionality was removed with version 1.7.0
            it.remove(booleanPreferencesKey(ENABLE_EXPERIMENTAL_ARTICLE_READER))
            // The data policy activity screen was removed with version 1.7.3
            it.remove(booleanPreferencesKey(DATA_POLICY_ACCEPTED))
            // The try pdf dialog was removed in version after 1.7.4
            it.remove(intPreferencesKey(TRY_PDF_DIALOG_COUNT))
            // The counting of the drawer opening was removed in version after 1.7.4
            it.remove(intPreferencesKey(DRAWER_SHOWN_COUNT))
            // The counting of the drawer opening was removed in version after 1.7.4
            it.remove(intPreferencesKey(FIRST_APP_START))
        }
    }
}
