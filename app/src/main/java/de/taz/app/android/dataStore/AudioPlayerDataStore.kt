package de.taz.app.android.dataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.DEFAULT_AUDIO_PLAYBACK_SPEED
import de.taz.app.android.util.SingletonHolder

private const val PREFERENCES_AUDIO_PLAYER = "preferences_audio_player"

private const val PLAYBACK_SPEED = "playback_speed"

private val Context.audioPlayerDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_AUDIO_PLAYER
)

class AudioPlayerDataStore private constructor(applicationContext: Context) {
    companion object : SingletonHolder<AudioPlayerDataStore, Context>(::AudioPlayerDataStore)

    private val dataStore = applicationContext.audioPlayerDataStore

    val playbackSpeed: DataStoreEntry<Float> = SimpleDataStoreEntry(
        dataStore, floatPreferencesKey(PLAYBACK_SPEED), DEFAULT_AUDIO_PLAYBACK_SPEED
    )
}