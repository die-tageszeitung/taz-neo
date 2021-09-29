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

    fun asLiveData(): LiveData<T>

    suspend fun update(value: T)

    suspend fun alreadySet(): Boolean

    suspend fun current(): T = asFlow().first()

    suspend fun reset()
}


class SimpleDataStoreEntry<T>(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<T>,
    private val default: T
) : DataStoreEntry<T> {
    override fun asFlow(): Flow<T> = dataStore.data.map { it[key] ?: default }

    override fun asLiveData(): LiveData<T> = asFlow().asLiveData()

    override suspend fun update(value: T) {
        dataStore.edit { it[key] = value }
    }

    override suspend fun alreadySet(): Boolean =
        dataStore.data.map { it[key] }.first() != null

    override suspend fun reset() = update(default)
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

    override fun asLiveData(): LiveData<S> = asFlow().asLiveData()

    override suspend fun update(value: S) = dataStoreEntry.update(mapStoT(value))

    override suspend fun alreadySet(): Boolean = dataStoreEntry.alreadySet()

    override suspend fun reset() = update(default)
}