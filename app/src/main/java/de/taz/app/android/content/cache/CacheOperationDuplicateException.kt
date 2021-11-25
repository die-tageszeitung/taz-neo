package de.taz.app.android.content.cache


abstract class CacheOperationBlockedException(
    message: String,
    val blockingOperation: AnyCacheOperation,
    cause: Throwable?
): IllegalStateException(message, cause)

/**
 * Should be thrown if while attempting to execute a [CacheOperation] another [CacheOperation]
 * with a different type is already ongoing to avoid parallel execution
 */
class DifferentOperationActiveException(
    message: String,
    blockingOperation: AnyCacheOperation,
    cause: Throwable? = null
): CacheOperationBlockedException(message, blockingOperation, cause)

/**
 * Should be thrown if while attempting to execute a [CacheOperation] another [CacheOperation]
 * with the same type is already ongoing to avoid duplicate execution
 */
class SameOperationActiveException(
    message: String,
    blockingOperation: AnyCacheOperation,
    cause: Throwable? = null
): CacheOperationBlockedException(message, blockingOperation, cause)