package de.taz.app.android.content.cache

/**
 * The interface to listen to [CacheOperation] updates
 * @param RESULT Is the result type of a [CacheOperation]. If no result is expected [Unit] should be used.
 */
interface CacheStateListener<RESULT> {
    fun onFailuire(e: Exception) = Unit
    fun onSuccess(result: RESULT) = Unit
    fun onUpdate(update: CacheStateUpdate) = Unit
}
