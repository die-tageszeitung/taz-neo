package de.taz.app.android.content.cache

import de.taz.app.android.download.DownloadPriority

/**
 * The abstract CacheItem is an Item typically processed in a [CacheOperation]. It might be implemented
 * with any extra properties that are specific to a certain use case
 *
 * @param key a unique key to avoid duplication in the process queue
 * @param priority The [DownloadPriority] this item should be handled with
 */
abstract class CacheItem(
    val key: String,
    var priority: () -> DownloadPriority
) : Comparable<CacheItem> {
    override fun compareTo(other: CacheItem): Int {
        return priority().compareTo(other.priority())
    }
}

/**
 * The [CacheOperationItem] ties a [CacheItem] to an operation. Useful to track the parent operation
 * while processing [CacheItem]s
 *
 * @param item The item of type [CacheItem]
 * @param operation The operation it belongs to
 */
class CacheOperationItem<ITEM: CacheItem> (
    val item: ITEM,
    val operation: AnyCacheOperation
) : Comparable<CacheOperationItem<ITEM>> {
    override fun compareTo(other: CacheOperationItem<ITEM>): Int {
        return item.compareTo(other.item)
    }
}


/**
 * A [FileCacheItem] represents a [de.taz.app.android.api.models.FileEntry], wrapped in a [FileEntryOperation] to provide
 * information about _from_ where and _to_ where a file should be transferred.
 *
 * @param key a unique key to avoid duplication in the process queue
 * @param priority The [DownloadPriority] this item should be handled with
 * @param fileEntryOperation A [de.taz.app.android.api.models.FileEntry] along its origin url and destination file path
 */
class FileCacheItem(
    key: String,
    priority: () -> DownloadPriority,
    val fileEntryOperation: FileEntryOperation
): CacheItem(key, priority)

/**
 * A [SubOperationCacheItem] represents a suboperation that is being processed in course of another parent operation
 * Should only occur wrapped in a [CacheOperationItem].
 *
 * @param key a unique key to avoid duplication in the process queue
 * @param priority The [DownloadPriority] this item should be handled with
 * @param subOperation The sub operation of type [CacheOperation]
 */
class SubOperationCacheItem(
    key: String,
    priority: () -> DownloadPriority,
    val subOperation: AnyCacheOperation
): CacheItem(key, priority)

/**
 * A [MetadataCacheItem] represents a Metadata transfer (both download and deletion) that is being
 * processed in course of another parent operation
 *
 * @param key a unique key to avoid duplication in the process queue
 * @param priority The [DownloadPriority] this item should be handled with
 * @param tag The tag of the Metadata being processed
 */
class MetadataCacheItem(
    key: String,
    priority: () -> DownloadPriority,
    val tag: String
): CacheItem(key, priority)