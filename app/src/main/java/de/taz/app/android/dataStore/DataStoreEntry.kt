package de.taz.app.android.dataStore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


interface DataStoreEntry<T> {
    fun asLiveData(): LiveData<T>

    /**
     * function to set the new value
     * @param value the new value for this DataStoreEntry
     */
    suspend fun set(value: T)

    /**
     * function to get the currently set value - or the default
     */
    suspend fun get(): T

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
    private fun asFlow(): Flow<T> = dataStore.data.map { it[key] ?: default }

    override fun asLiveData(): LiveData<T> = asFlow().asLiveData()

    override suspend fun get(): T = asFlow().first()

    override suspend fun set(value: T) {
        dataStore.edit { it[key] = value }
    }

    override suspend fun reset() = set(default)
}

class MappingDataStoreEntry<S, T>(
    private val dataStore: DataStore<Preferences>,
    key: Preferences.Key<T>,
    default: S,
    private val mapStoT: (S) -> T,
    private val mapTtoS: (T) -> S
) : DataStoreEntry<S> {
    private val dataStoreEntry = SimpleDataStoreEntry(dataStore, key, mapStoT(default))

    override fun asLiveData(): LiveData<S> = dataStoreEntry.asLiveData().map { mapTtoS(it) }

    override suspend fun set(value: S) = dataStoreEntry.set(mapStoT(value))

    override suspend fun get(): S = mapTtoS(dataStoreEntry.get())

    override suspend fun reset() = dataStoreEntry.reset()
}
