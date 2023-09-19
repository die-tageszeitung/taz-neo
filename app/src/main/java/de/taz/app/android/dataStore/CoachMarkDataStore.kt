package de.taz.app.android.dataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.util.SingletonHolder

private const val PREFERENCES_COACH_MARK = "preferences_coach_mark"

private const val FAB_COACH_MARK_SHOWN = "fab_coach_mark_shown"
private const val ARCHIVE_COACH_MARK_SHOWN = "archive_coach_mark_shown"
private const val TAZ_LOGO_COACH_MARK_SHOWN = "taz_logo_coach_mark_shown"
private const val HORIZONTAL_SWIPE_COACH_MARK_SHOWN = "horizontal_swipe_coach_mark_shown"
private const val ARTICLE_AUDIO_COACH_MARK_SHOWN = "article_audio_coach_mark_shown"

private val Context.coachMarkDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_COACH_MARK
)

class CoachMarkDataStore private constructor(applicationContext: Context) {

    companion object : SingletonHolder<CoachMarkDataStore, Context>(::CoachMarkDataStore)

    private val dataStore = applicationContext.coachMarkDataStore

    val fabCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(FAB_COACH_MARK_SHOWN), 0
    )
    val archiveCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(ARCHIVE_COACH_MARK_SHOWN), 0
    )
    val drawerLogoCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(TAZ_LOGO_COACH_MARK_SHOWN), 0
    )
    val horizontalSwipeCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(HORIZONTAL_SWIPE_COACH_MARK_SHOWN), 0
    )
    val articleAudioCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(ARTICLE_AUDIO_COACH_MARK_SHOWN), 0
    )
}