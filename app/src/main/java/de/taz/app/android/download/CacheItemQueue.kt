package de.taz.app.android.download

import de.taz.app.android.MAX_SIMULTANEOUS_DOWNLOADS
import de.taz.app.android.content.cache.ContentDownload
import de.taz.app.android.content.cache.FileCacheItem
import de.taz.app.android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import java.util.Collections
import java.util.concurrent.PriorityBlockingQueue

/**
 * A blocking queue of CacheItems. The order is reversed, so a poll will return the item with the
 * HIGHEST natural order of CacheItem (DownloadPriority.HIGH) instead of the lowest.
 * Offer is overridden
 */
object CacheItemQueue {
    private val queue = PriorityBlockingQueue<FileCacheItem>(
        MAX_SIMULTANEOUS_DOWNLOADS,
        Collections.reverseOrder()
    )
    private val inQueueKeys = HashSet<String>()
    private val additionalOperations = HashMap<String, MutableList<ContentDownload>>()
    private val channel = Channel<Pair<FileCacheItem, List<ContentDownload>>>(Channel.UNLIMITED)

    private val log by Log
    private val lock = Any()

    /**
     * This Runnable implements a bridge between thread world and Coroutine world.
     * This single thread will wait blocking on the priority queue and sends any updates to a channel
     * which then can be waited on in a suspend function.
     */
    private object Consumer : Runnable {
        override fun run() {
            while (true) {
                val item = try {
                    queue.take()
                } catch (_: InterruptedException) {
                    break
                }
                val ops = synchronized(lock) {
                    inQueueKeys.remove(item.key)
                    additionalOperations.remove(item.key) ?: emptyList()
                }
                channel.trySendBlocking(item to ops)
            }
        }
    }

    init {
        // Start the Consumer that should run indefinitely
        Thread(Consumer).apply {
            isDaemon = true
            name = "CacheItemQueueConsumer"
            start()
        }
    }

    /**
     * It is favorable to not download the same file twice. This function will
     * check if the same item is already queued - if so do not enqueue it but add the operation
     * to the internal additionalOperations that can be returned on poll to inform other interested operations
     * in the updates for the CacheItem
     * @param item The [FileCacheItem] to enqueue
     * @param operation The [ContentDownload] to notify upon completion
     * @return A boolean indicating if the item has been enqueued
     */
    fun sendOrNotify(item: FileCacheItem, operation: ContentDownload): Boolean =
        synchronized(lock) {
            val ops = additionalOperations.getOrPut(item.key) { mutableListOf() }
            ops.add(operation)

            return if (inQueueKeys.add(item.key)) {
                queue.offer(item)
            } else {
                log.verbose("Deduplicated ${item.fileEntryOperation.fileEntry.name}")
                false
            }
        }

    /**
     * Receive a new queue item if available, function suspends until a new item becomes available
     * @return The cache item in the queue with the highest priority
     */
    suspend fun receive(): Pair<FileCacheItem, List<ContentDownload>> = channel.receive()
}
