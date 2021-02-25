package de.taz.app.android.util

import io.sentry.Sentry


suspend fun <T> reportAndRethrowExceptionsAsync(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}


fun <T> reportAndRethrowExceptions(block: () -> T): T {
    try {
        return block()
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}
