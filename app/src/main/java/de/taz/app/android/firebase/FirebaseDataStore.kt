package de.taz.app.android.firebase

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.dataStore.MappingDataStoreEntry
import de.taz.app.android.dataStore.SimpleDataStoreEntry
import de.taz.app.android.util.SingletonHolder


// region old setting names
private const val PREFERENCES_FCM = "fcm"
// endregion

// region setting keys
private const val FCM_TOKEN = "fcm token"
private const val FCM_TOKEN_OLD = "fcm token old"
private const val FCM_TOKEN_SENT = "fcm token sent"
// endregion

private val Context.fcmDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_FCM,
    produceMigrations = {
        listOf(
            SharedPreferencesMigration(
                it,
                PREFERENCES_FCM
            ),
        )
    }
)

@Mockable
class FirebaseDataStore @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) constructor(
    applicationContext: Context
) {

    companion object : SingletonHolder<FirebaseDataStore, Context>(::FirebaseDataStore)

    private val dataStore = applicationContext.fcmDataStore

    // otherwise the graphql endpoint has a problem
    val token = MappingDataStoreEntry<String?, String>(
        dataStore,
        stringPreferencesKey(FCM_TOKEN),
        "",
        { it ?: "" },
        { it.takeIf { it.isNotEmpty() } }
    )
    val oldToken = MappingDataStoreEntry<String?, String>(
        dataStore,
        stringPreferencesKey(FCM_TOKEN_OLD),
        "",
        { it ?: "" },
        { it.takeIf { it.isNotEmpty() } }
    )
    val tokenSent = SimpleDataStoreEntry(dataStore, booleanPreferencesKey(FCM_TOKEN_SENT), false)

    suspend fun isPush(): Boolean = token.get()?.isNotEmpty() ?: false
}
