package de.taz.app.android.download

import de.taz.app.android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CounterLock(private val maxValue: Int) {
    private val log by Log
    var currentValue = 0
        private set

    private var waitingContinuations = ConcurrentLinkedQueue<Continuation<Unit>>()

    @Synchronized
    suspend fun waitForSlot() {
        suspendCoroutine<Unit> { continuation ->
            if (currentValue >= maxValue) {
                waitingContinuations.offer(continuation)
            } else {
                continuation.resume(Unit)
                currentValue++
            }
        }
    }

    fun freeSlot() {
        waitingContinuations.poll()?.resume(Unit) ?: currentValue--
        log.debug("Freed a slot, currently busy: $currentValue, currently waiting ${waitingContinuations.size}")
    }

    suspend fun withLock(block: suspend () -> Unit) {
        try {
            waitForSlot()
            block()
        } finally {
            freeSlot()
        }
    }
}