package de.taz.app.android.dataStore

import android.app.Application
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
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
private const val HAS_INTERNAL_TAZ_USER_GOAL_BEEN_TRACKED = "has_internal_taz_user_goal_been_tracked"
private const val TEST_GOAL_TRACKING_ENABLED = "test_goal_tracking_enabled"
private const val MULTI_COLUMN_MODE_BOTTOM_SHEET_SHOWN = "multi_column_mode_bottom_sheet_shown"
private const val SINGLE_COLUMN_MODE_BOTTOM_SHEET_DO_NOT_SHOW_AGAIN = "single_column_mode_bottom_sheet_do_not_show_again"
private const val BOOKMARKS_SYNCHRONIZATION_BOTTOM_SHEET_DO_NOT_SHOW_AGAIN = "bookmarks_synchronization_bottom_sheet_do_not_show_again"
private const val SETTINGS_BOOKMARKS_SYNCHRONIZATION = "settings_bookmark_synchronization"
private const val SETTINGS_BOOKMARKS_SYNCHRONIZATION_CHANGED = "settings_bookmark_synchronization_changed"
private const val SETTINGS_SHOW_ANIMATED_MOMENTS = "settings_show_animated_moments"
private const val SETTINGS_CONTINUE_READ = "settings_continue_read"
private const val SETTINGS_CONTINUE_READ_ASK_EACH_TIME = "settings_continue_read_ask_each_time"
private const val SETTINGS_CONTINUE_READ_DIALOG_SHOWN = "settings_continue_read_dialog_shown"
private const val SETTINGS_CONTINUE_READ_CLICKED = "settings_continue_read_clicked"
private const val SETTINGS_CONTINUE_READ_DISMISSED = "settings_continue_read_dismissed"
private const val SETTINGS_HOME_FRAGMENT_STATUS = "settings_home_fragment_status"

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

    @SuppressWarnings("unused") // used in MatomoTracker
    val hasInternalTazUserGoalBeenTracked: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(HAS_INTERNAL_TAZ_USER_GOAL_BEEN_TRACKED), false
    )

    val testTrackingGoalEnabled: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(TEST_GOAL_TRACKING_ENABLED), false
    )

    val multiColumnModeBottomSheetAlreadyShown: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(MULTI_COLUMN_MODE_BOTTOM_SHEET_SHOWN), false
    )

    val singleColumnModeBottomSheetDoNotShowAgain: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(SINGLE_COLUMN_MODE_BOTTOM_SHEET_DO_NOT_SHOW_AGAIN), false
    )

    val bookmarksSynchronizationBottomSheetDoNotShowAgain: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(BOOKMARKS_SYNCHRONIZATION_BOTTOM_SHEET_DO_NOT_SHOW_AGAIN), false
    )

    val bookmarksSynchronizationEnabled: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(SETTINGS_BOOKMARKS_SYNCHRONIZATION), false
    )

    val bookmarksSynchronizationChangedToEnabled: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(SETTINGS_BOOKMARKS_SYNCHRONIZATION_CHANGED), false
    )

    val showAnimatedMoments: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(SETTINGS_SHOW_ANIMATED_MOMENTS), true
    )

    val settingsContinueReadAskEachTime: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(SETTINGS_CONTINUE_READ_ASK_EACH_TIME), true
    )

    val settingsContinueReadDialogShown: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(SETTINGS_CONTINUE_READ_DIALOG_SHOWN), false
    )

    val settingsContinueRead: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(SETTINGS_CONTINUE_READ), false
    )

    val continueReadClicked: DataStoreEntry<Int> = SimpleDataStoreEntry(
        dataStore, intPreferencesKey(SETTINGS_CONTINUE_READ_CLICKED), 0
    )

    val continueReadDismissed: DataStoreEntry<Int> = SimpleDataStoreEntry(
        dataStore, intPreferencesKey(SETTINGS_CONTINUE_READ_DISMISSED), 0
    )
    val homeFragmentState: DataStoreEntry<HomeFragment.State> = MappingDataStoreEntry(
        dataStore,
        stringPreferencesKey(SETTINGS_HOME_FRAGMENT_STATUS),
        HomeFragment.State.COVERFLOW,
        { it.name },
        { HomeFragment.State.valueOf(it) },
    )

    init {
        CoroutineScope(
            Dispatchers.Default + CoroutineName("GeneralDataStore Cleanup")
        ).launch {
            clearRemovedEntries()
        }
    }


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
