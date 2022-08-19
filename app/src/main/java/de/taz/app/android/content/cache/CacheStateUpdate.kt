package de.taz.app.android.content.cache

/**
 * A structure representing an update to the current state of a [CacheOperation]
 *
 * Both [processedFiles] and [totalFiles] might be 0 for [operation]s that are not dealing with files
 * (i.e. [ContentDownload])
 * @param type Type of the update itself indicating what just happened
 * @param cacheState The overall state of the item [operation] is currently processing
 * @param processedFiles The amount of files that have been processed (useful for progress bars).
 * @param totalFiles The total files to be processed
 * @param operation The [CacheOperation] the update originates form. This might be null if no operation is ongoing for the requested item
 * @param exception Optionally an exception with the cause if [type] is [Type.ITEM_FAILED] or [Type.FAILED]
 */
data class CacheStateUpdate(
    val type: Type,
    val cacheState: CacheState,
    val processedFiles: Int,
    val totalFiles: Int,
    val operation: AnyCacheOperation?,
    val exception: Exception? = null
) {
    enum class Type {
        INITIAL,
        BAD_CONNECTION,
        ITEM_SUCCESSFUL,
        ITEM_FAILED,
        SUCCEEDED,
        FAILED
    }

    /**
     * Cache state is either [Type.FAILED] or [Type.SUCCEEDED]
     */
    val hasCompleted
        get() = listOf(Type.SUCCEEDED, Type.FAILED).contains(type)

    val hasFailed: Boolean
        get() = type == Type.FAILED
}