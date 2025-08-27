package de.taz.app.android.dataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val PREFERENCES_COACH_MARK = "preferences_coach_mark"

private const val TAZ_LOGO_COACH_MARK_SHOWN = "taz_logo_coach_mark_shown"
private const val LMD_LOGO_COACH_MARK_SHOWN = "lmd_logo_coach_mark_shown"
private const val HORIZONTAL_SECTION_SWIPE_COACH_MARK_SHOWN = "horizontal_swipe_coach_mark_shown"
private const val HORIZONTAL_ARTICLE_SWIPE_COACH_MARK_SHOWN = "horizontal_article_swipe_coach_mark_shown"
private const val ARTICLE_AUDIO_COACH_MARK_SHOWN = "article_audio_coach_mark_shown"
private const val ARTICLE_SIZE_COACH_MARK_SHOWN = "article_size_coach_mark_shown"
private const val ARTICLE_SHARE_COACH_MARK_SHOWN = "article_share_coach_mark_shown"
private const val BOOKMARKS_SHOWN = "bookmarks_shown"
private const val BOOKMARKS_SWIPE_COACH_MARK_SHOWN = "bookmarks_swipe_coach_mark_shown"
private const val SEARCH_ACTIVITY_SHOWN = "search_activity_shown"
private const val SEARCH_FILTER_COACH_MARK_SHOWN = "search_filter_coach_mark_shown"
private const val ALWAYS_SHOW_COACH_MARKS = "always_show_coach_marks"
private const val COACH_MARKS_SHOWN_IN_SESSION = "coach_marks_shown_in_session"

/**
 * Deprecated / removed settings
 */
private const val ARCHIVE_COACH_MARK_SHOWN = "archive_coach_mark_shown"
private const val ARTICLE_TAP_TO_SCROLL_COACH_MARK_SHOWN = "article_tap_to_scroll_coach_mark_shown"
private const val COACH_MARK_SHOWN_LOCK = "coach_mark_shown_lock"
private const val FAB_COACH_MARK_SHOWN = "fab_coach_mark_shown"

private val Context.coachMarkDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_COACH_MARK
)

class CoachMarkDataStore private constructor(applicationContext: Context) {

    companion object : SingletonHolder<CoachMarkDataStore, Context>(::CoachMarkDataStore)

    private val dataStore = applicationContext.coachMarkDataStore

    val tazLogoCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(TAZ_LOGO_COACH_MARK_SHOWN), 0
    )
    val lmdLogoCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(LMD_LOGO_COACH_MARK_SHOWN), 0
    )
    val horizontalSectionSwipeCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(HORIZONTAL_SECTION_SWIPE_COACH_MARK_SHOWN), 0
    )
    val horizontalArticleSwipeCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(HORIZONTAL_ARTICLE_SWIPE_COACH_MARK_SHOWN), 0
    )
    val articleAudioCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(ARTICLE_AUDIO_COACH_MARK_SHOWN), 0
    )
    val articleSizeCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(ARTICLE_SIZE_COACH_MARK_SHOWN), 0
    )
    val articleShareCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(ARTICLE_SHARE_COACH_MARK_SHOWN), 0
    )
    val bookmarksShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(BOOKMARKS_SHOWN), 0
    )
    val bookmarksSwipeCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(BOOKMARKS_SWIPE_COACH_MARK_SHOWN), 0
    )
    val searchActivityShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(SEARCH_ACTIVITY_SHOWN), 0
    )
    val searchFilterCoachMarkShown: DataStoreEntry<Long> = SimpleDataStoreEntry(
        dataStore, longPreferencesKey(SEARCH_FILTER_COACH_MARK_SHOWN), 0
    )
    val alwaysShowCoachMarks: DataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(ALWAYS_SHOW_COACH_MARKS), false
    )
    val coachMarksShownInSession: DataStoreEntry<Int> = SimpleDataStoreEntry(
        dataStore, intPreferencesKey(COACH_MARKS_SHOWN_IN_SESSION), 0
    )

    init {
        CoroutineScope(
            Dispatchers.Default + CoroutineName("CoachMarkDataStore Cleanup")
        ).launch {
            clearRemovedEntries()
        }
    }


    suspend fun clearRemovedEntries() {
        dataStore.edit {
            // This functionality was removed with version 1.12.0
            it.remove(longPreferencesKey(ARCHIVE_COACH_MARK_SHOWN))
            it.remove(booleanPreferencesKey(COACH_MARK_SHOWN_LOCK))
            it.remove(longPreferencesKey(FAB_COACH_MARK_SHOWN))
            // this functionality was removed earlier
            it.remove(longPreferencesKey(ARTICLE_TAP_TO_SCROLL_COACH_MARK_SHOWN))
        }
    }
}