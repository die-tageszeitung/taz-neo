package de.taz.app.android.dataStore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


interface DataStoreEntry<T> {
    fun asFlow(): Flow<T>
    fun asLiveData(): LiveData<T> = asFlow().asLiveData()

    /**
     * function to set the new value
     * @param value the new value for this DataStoreEntry
     */
    suspend fun set(value: T)

    /**
     * function to check whether a value has been stored in the database
     * @return true if a value exists in the database, false otherwise
     */
    suspend fun hasBeenSet(): Boolean

    /**
     * function to get the currently set value - or the default
     */
    suspend fun get(): T = asFlow().first()

    /**
     * function to reset DataSoreEntry to default value
     */
    suspend fun reset()
}


class SimpleDataStoreEntry<T>(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<T>,
    private val default: T
) : DataStoreEntry<T> {
    override fun asFlow(): Flow<T> = dataStore.data.map { it[key] ?: default }

    override suspend fun set(value: T) {
        dataStore.edit { it[key] = value }
    }

    override suspend fun hasBeenSet(): Boolean =
        dataStore.data.map { it[key] }.first() != null

    override suspend fun reset() = set(default)
}

class MappingDataStoreEntry<S, T>(
    dataStore: DataStore<Preferences>,
    key: Preferences.Key<T>,
    private val default: S,
    private val mapStoT: (S) -> T,
    private val mapTtoS: (T) -> S
) : DataStoreEntry<S> {
    private val dataStoreEntry = SimpleDataStoreEntry(dataStore, key, mapStoT(default))

    override fun asFlow(): Flow<S> = dataStoreEntry.asFlow().map { mapTtoS(it) }

    override suspend fun set(value: S) = dataStoreEntry.set(mapStoT(value))

    override suspend fun hasBeenSet(): Boolean = dataStoreEntry.hasBeenSet()

    override suspend fun reset() = set(default)
}