package de.taz.app.android.data

import de.taz.app.android.CONNECTION_FAILURE_BACKOFF_TIME_MS
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val MAX_CONNECTION_CHECK_INTERVAL = 30000L
const val BACK_OFF_FACTOR = 2f

@Mockable
abstract class ConnectionHelper {
    val log by Log

    private val waitingCalls = ConcurrentLinkedQueue<Continuation<Unit>>()

    private var isCurrentlyReachable = true
    private var backOffTimeMs = CONNECTION_FAILURE_BACKOFF_TIME_MS

    private var connectivityCheckJob: Job? = null

    suspend fun <T> retryOnConnectivityFailure(onConnectionFailure: suspend () -> Unit = {}, block: suspend () -> T): T {
        while (true) {
            try {
                return block()
            } catch (e: ConnectivityException.Recoverable) {
                onConnectionFailure()
                isCurrentlyReachable = false
                ensureConnectivityCheckRunning(onConnectionFailure)
                suspendCoroutine<Unit> { continuation -> waitingCalls.offer(continuation) }
            }
        }
    }

    private suspend fun ensureConnectivityCheckRunning(onConnectionFailure: suspend () -> Unit = {}) {
        if (connectivityCheckJob?.isActive != true) {
            connectivityCheckJob = CoroutineScope(Dispatchers.IO).launch {
                tryForConnectivity(onConnectionFailure)
            }
        }
    }

    private suspend fun tryForConnectivity(onConnectionFailure: suspend () -> Unit = {}) {
        while (!isCurrentlyReachable) {
            log.debug("Connection lost, retrying in $backOffTimeMs ms")
            delay(backOffTimeMs)
            incrementBackOffTime()
            isCurrentlyReachable = checkConnectivity()
            if (!isCurrentlyReachable) {
                onConnectionFailure()
            }
        }
        resetBackOffTime()
        log.debug("Connection recovered, resuming ${waitingCalls.size} calls")
        waitingCalls.forEach {
            try {
                it.resume(Unit)
            } catch (e: IllegalStateException) {
                log.warn("Connection helper tried to resume Job twice")
            }
            waitingCalls.remove(it)
        }
    }

    abstract suspend fun checkConnectivity(): Boolean

    private fun incrementBackOffTime() {
        backOffTimeMs = (BACK_OFF_FACTOR * backOffTimeMs).toLong().coerceAtMost(
            MAX_CONNECTION_CHECK_INTERVAL
        )
    }

    private fun resetBackOffTime() {
        backOffTimeMs = CONNECTION_FAILURE_BACKOFF_TIME_MS
    }
}