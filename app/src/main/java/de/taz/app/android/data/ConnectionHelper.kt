package de.taz.app.android.data

import de.taz.app.android.CONNECTION_FAILURE_BACKOFF_TIME_MS
import de.taz.app.android.MAX_CONNECTION_FAILURE_BACKOFF_TIME_MS
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

const val BACK_OFF_FACTOR = 1.75f

data class WaitingCall(
    val continuation: Continuation<Unit>,
    val maxRetries: Int
)

@Mockable
abstract class ConnectionHelper {
    val log by Log

    private val waitingCalls = ConcurrentLinkedQueue<WaitingCall>()

    private var failedAttempts = 0
    private val isCurrentlyReachable
        get() = failedAttempts == 0
    private var backOffTimeMs = CONNECTION_FAILURE_BACKOFF_TIME_MS


    private var connectivityCheckJob: Job? = null

    suspend fun <T> retryOnConnectivityFailure(
        onConnectionFailure: suspend () -> Unit = {},
        maxRetries: Int = -1,
        block: suspend () -> T
    ): T {
        while (true) {
            try {
                return block()
            } catch (e: ConnectivityException.Recoverable) {
                onConnectionFailure()
                ensureConnectivityCheckRunning()
                if (maxRetries > -1 && failedAttempts >= maxRetries) {
                    throw ConnectivityException.Recoverable("Maximum retries exceeded", e)
                } else {
                    suspendCoroutine<Unit> { continuation ->
                        waitingCalls.offer(
                            WaitingCall(
                                continuation,
                                maxRetries
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun ensureConnectivityCheckRunning() {
        if (connectivityCheckJob?.isActive != true) {
            connectivityCheckJob = CoroutineScope(Dispatchers.IO).launch {
                tryForConnectivity()
            }
        }
    }

    private suspend fun tryForConnectivity() {
        do {
            log.debug("Connection lost, retrying in $backOffTimeMs ms")
            delay(backOffTimeMs)
            incrementBackOffTime()
            if (!checkConnectivity()) {
                failedAttempts++
                // Signal all waiting calls if they maximum retry limit is reached
                for (call in waitingCalls) {
                    if (call.maxRetries > -1 && failedAttempts > call.maxRetries) {
                        call.continuation.resumeWithException(ConnectivityException.Recoverable(
                            "Maximum retries amount exceeded"
                        ))
                        waitingCalls.remove(call)
                    }
                }
            } else {
                failedAttempts = 0
            }

        } while (!isCurrentlyReachable)

        resetBackOffTime()
        log.debug("Connection recovered, resuming ${waitingCalls.size} calls")
        waitingCalls.forEach {
            try {
                it.continuation.resume(Unit)
            } catch (e: IllegalStateException) {
                log.warn("Connection helper tried to resume Job twice")
            }
            waitingCalls.remove(it)
        }
    }

    abstract suspend fun checkConnectivity(): Boolean

    private fun incrementBackOffTime() {
        backOffTimeMs = (BACK_OFF_FACTOR * backOffTimeMs).toLong().coerceAtMost(
            MAX_CONNECTION_FAILURE_BACKOFF_TIME_MS
        )
    }

    private fun resetBackOffTime() {
        backOffTimeMs = CONNECTION_FAILURE_BACKOFF_TIME_MS
    }
}