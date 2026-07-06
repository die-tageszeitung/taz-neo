package de.taz.app.android.download

import de.taz.app.android.content.cache.ContentDownload
import de.taz.app.android.content.cache.FileCacheItem
import de.taz.app.android.util.Log
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.LinkedBlockingDeque

/**
 * A blocking queue of CacheItems for every DownloadPriority.
 */
object CacheItemQueue {
    private val queueMap = DownloadPriority.entries.associateWith {
        LinkedBlockingDeque<FileCacheItem>()
    }

    private val inQueueKeysMap = HashMap<String, DownloadPriority>()
    private val additionalOperations = HashMap<String, MutableList<ContentDownload>>()

    // Unlimited signal channel to wake up suspended receivers
    private val signalChannel = Channel<Unit>(Channel.UNLIMITED)
    private val reversedPriorities = DownloadPriority.entries.reversed()

    private val log by Log
    private val lock = Any()

    /**
     * It is favorable to not download the same file twice. This function will
     * check if the same item is already queued - if so do not enqueue it but add the operation
     * to the internal additionalOperations that can be returned on poll to inform other interested operations
     * in the updates for the CacheItem
     * @param operation The [ContentDownload] to notify upon completion
     */
    fun sendOrNotify(operation: ContentDownload, reEnqueueing: Boolean) {
        var addedCount = 0
        synchronized(lock) {
            for (item in operation.cacheItems) {
                log.debug("Offering ${item.fileEntryOperation.fileEntry.name} with priority ${item.priority()}")
                val ops = additionalOperations.getOrPut(item.key) { mutableListOf() }
                if (!ops.contains(operation)) {
                    ops.add(operation)
                }

                val existing = inQueueKeysMap[item.key]
                // if we do not find the item in the queue but we are rescheduling it is being downloaded
                if (existing == null && reEnqueueing) {
                    continue
                }

                // Only enqueue if new or upgrading priority from Normal to High
                if (existing == null || (existing == DownloadPriority.Normal && operation.priority == DownloadPriority.High)) {
                    inQueueKeysMap[item.key] = operation.priority
                    addedCount++
                }
                // always queue items so that LIFO order persis
                queueMap[operation.priority]?.offer(item)
            }
        }

        // Signal receivers that new work is available after releasing the lock
        // to avoid waking up threads that would immediately block on the lock again.
        repeat(addedCount) {
            signalChannel.trySend(Unit)
        }
    }

    private fun pollNextTask(): Pair<FileCacheItem, List<ContentDownload>>? {
        for (priority in reversedPriorities) {
            val deque = queueMap[priority] ?: break
            while (deque.isNotEmpty()) {
                val item = deque.pollLast() ?: break

                // If we can remove the key, it means this is the first (and highest priority)
                // entry we've encountered for this file.
                if (inQueueKeysMap.remove(item.key) != null) {
                    val ops = additionalOperations.remove(item.key) ?: emptyList()
                    return item to ops
                }
            }
        }
        return null
    }

    /**
     * Receive a new queue item if available, function suspends until a new item becomes available.
     * Always returns the highest priority task currently in the queue at the moment of waking.
     * @return The cache item in the queue with the highest priority
     */
    suspend fun receive(): Pair<FileCacheItem, List<ContentDownload>> {
        while (true) {
            val task = synchronized(lock) {
                pollNextTask()
            }
            if (task != null) {
                return task
            }

            // Wait for a signal that an item was added
            signalChannel.receive()
        }
    }
}
