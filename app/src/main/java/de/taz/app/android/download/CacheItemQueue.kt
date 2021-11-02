package de.taz.app.android.download

import de.taz.app.android.MAX_SIMULTANEOUS_DOWNLOADS
import de.taz.app.android.content.cache.*
import de.taz.app.android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import kotlin.collections.HashMap

/**
 * A blocking queue of CacheItems. The order is reversed, so a poll will return the item with the
 * HIGHEST natural order of CacheItem (DownloadPriority.HIGH) instead of the lowest.
 * Offer is overridden
 */
object CacheItemQueue : PriorityBlockingQueue<CacheOperationItem<FileCacheItem>>(
    MAX_SIMULTANEOUS_DOWNLOADS,
    Collections.reverseOrder()
) {
    private val enqueueLock = Mutex()
    private val additionalOperations = HashMap<String, List<ContentDownload>>()

    private val log by Log

    /**
     * It is favorable to not download the same file twice. This function will
     * check if the same item is already queued - if so do not enqueue it but add the operation
     * to the internal additionalOperations that can be returned on poll to inform other interested operations
     * in the updates for the CacheItem
     * @param e The [CacheOperationItem] to enqueue
     * @return A boolean indicating if the item has been enqueued
     */
    suspend fun offerOrNotify(e: CacheOperationItem<FileCacheItem>): Boolean =
        enqueueLock.withLock {
            additionalOperations[e.item.key] =
                (additionalOperations[e.item.key] ?: emptyList()) + e.operation as ContentDownload
            return if (find { it.item.key == e.item.key } == null) {
                super.offer(e)
            } else {
                log.verbose("Deduplicated ${e.item.fileEntryOperation.fileEntry.name}")
                false
            }
        }


    /**
     * Poll a CacheItem and along with all potential other operations that need to recieve
     * updates on the item
     * @return A pair with a CacheItem (first) and a list of operations that need to be notified (second)
     */
    suspend fun pollWithOperations(): Pair<FileCacheItem, List<ContentDownload>>? =
        enqueueLock.withLock {
            val item = poll()
            return item?.let {
                it.item to (additionalOperations.remove(it.item.key) ?: emptyList())
            }

        }
}