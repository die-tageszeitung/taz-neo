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
    fun asFlow(): Flow<T>

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

/**
 * A simple [DataStoreEntry] which persists a [T] in the [dataStore]
 * [T] must be [String], [Int], [Boolean], [Long], [Float] or [Set]<String>
 * @param default - The default value which will be returned if no value is set in the dataStore yet
 * @param initFunction - This function will be executed if no value has been stored in [dataStore]
 *          yet. You might want to use it to set an initial value. If this function returns a value
 *          this value will be returned instead of [default]. If this function returns null or is
 *          not set [default] will be returned instead.
 */
class SimpleDataStoreEntry<T>(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<T>,
    private val default: T,
    private val initFunction: (suspend () -> T?)? = null
) : DataStoreEntry<T> {
    override fun asFlow(): Flow<T> = dataStore.data.map { it[key] ?: initFunction?.invoke() ?: default }

    override fun asLiveData(): LiveData<T> = asFlow().asLiveData()

    override suspend fun get(): T = asFlow().first()

    override suspend fun set(value: T) {
        dataStore.edit { it[key] = value }
    }

    override suspend fun reset() = set(default)
}

/**
 * A [DataStoreEntry] which persists a [S] in the [dataStore] by mapping [S] to [T] and vice versa.
 * [T] must be [String], [Int], [Boolean], [Long], [Float] or [Set]<String>
 * @param default - The default value which will be returned if no value is set in the dataStore yet
 * @param initFunction - This function will be executed if no value has been stored in [dataStore]
 *          yet. You might want to use it to set an initial value. If this function returns a value
 *          this value will be returned instead of [default]. If this function returns null or is
 *          not set [default] will be returned instead.
 */
class MappingDataStoreEntry<S, T>(
    dataStore: DataStore<Preferences>,
    key: Preferences.Key<T>,
    default: S,
    private val mapStoT: (S) -> T,
    private val mapTtoS: (T) -> S,
    private val initFunction: (suspend () -> S?)? = null
) : DataStoreEntry<S> {

    private suspend fun mapInit(): T? { return initFunction?.invoke()?.let { mapStoT(it) } }

    private val dataStoreEntry: SimpleDataStoreEntry<T> = SimpleDataStoreEntry(
        dataStore,
        key,
        mapStoT(default),
        ::mapInit
    )


    override fun asLiveData(): LiveData<S> = dataStoreEntry.asLiveData().map { mapTtoS(it) }

    override suspend fun set(value: S) = dataStoreEntry.set(mapStoT(value))

    override suspend fun get(): S = mapTtoS(dataStoreEntry.get())

    override suspend fun reset() = dataStoreEntry.reset()
    override fun asFlow(): Flow<S> = dataStoreEntry.asFlow().map { mapTtoS(it) }
}
