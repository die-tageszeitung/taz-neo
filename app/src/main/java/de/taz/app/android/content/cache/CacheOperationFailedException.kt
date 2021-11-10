package de.taz.app.android.content.cache

/**
 * Should be thrown if an operation failed. Preferably with the cause to determine the
 * original cause
 */
class CacheOperationFailedException(
    message: String,
    cause: Exception? = null
): Exception(message, cause)