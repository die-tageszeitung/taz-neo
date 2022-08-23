package de.taz.app.android.content.cache

/**
 * A structure representing an update to the current state of a [CacheOperation]
 *
 * @param type Type of the update itself indicating what just happened
 * @param cacheState The overall state of the operation is currently processed
 * @param exception Optionally an exception with the cause if [type] is [Type.ITEM_FAILED] or [Type.FAILED]
 */
data class CacheStateUpdate(
    val type: Type,
    val cacheState: CacheState,
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