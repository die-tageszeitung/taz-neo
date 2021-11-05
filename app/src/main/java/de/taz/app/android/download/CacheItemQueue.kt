package de.taz.app.android.download

import de.taz.app.android.MAX_SIMULTANEOUS_DOWNLOADS
import de.taz.app.android.content.cache.*
import de.taz.app.android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.PriorityBlockingQueue
import kotlin.collections.HashMap

/**
 * A blocking queue of CacheItems. The order is reversed, so a poll will return the item with the
 * HIGHEST natural order of CacheItem (DownloadPriority.HIGH) instead of the lowest.
 * Offer is overridden
 */
object CacheItemQueue {
    private val queue = PriorityBlockingQueue<CacheOperationItem<FileCacheItem>>(
        MAX_SIMULTANEOUS_DOWNLOADS,
        Collections.reverseOrder()
    )
    private val enqueueLock = Mutex()
    private val additionalOperations = HashMap<String, List<ContentDownload>>()
    private val channel = Channel<Pair<FileCacheItem, List<ContentDownload>>>()

    private val log by Log

    /**
     * This Runnable implements a bridge between thread world and Coroutine world.
     * This single thread will wait blocking on the priority queue and sends any updates to a channel
     * which then can be waited on in a suspend function.
     */
    private object Consumer : Runnable {
        override fun run() {
            while (true) {
                val item = queue.take()
                channel.trySendBlocking(
                    item.item to (additionalOperations.remove(item.item.key) ?: emptyList())
                )
            }
        }
    }

    init {
        // Start the Consumer that should run indefinitely
        Thread(Consumer).start()
    }



    /**
     * It is favorable to not download the same file twice. This function will
     * check if the same item is already queued - if so do not enqueue it but add the operation
     * to the internal additionalOperations that can be returned on poll to inform other interested operations
     * in the updates for the CacheItem
     * @param e The [CacheOperationItem] to enqueue
     * @return A boolean indicating if the item has been enqueued
     */
    suspend fun sendOrNotify(e: CacheOperationItem<FileCacheItem>): Boolean =
        enqueueLock.withLock {
            additionalOperations[e.item.key] =
                (additionalOperations[e.item.key] ?: emptyList()) + e.operation as ContentDownload
            return if (queue.find { it.item.key == e.item.key } == null) {
                queue.offer(e)
            } else {
                log.verbose("Deduplicated ${e.item.fileEntryOperation.fileEntry.name}")
                false
            }
        }


    /**
     * Receive a new queue item if available, function suspends until a new item becomes available
     * @return The cache item in the queue with the highest priority
     */
    suspend fun receive(): Pair<FileCacheItem, List<ContentDownload>> = channel.receive()
}