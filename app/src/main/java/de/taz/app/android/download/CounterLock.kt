package de.taz.app.android.download

import de.taz.app.android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CounterLock(private val maxValue: Int) {
    private val log by Log
    var currentValue = 0
        private set

    private var waitingContinuations = LinkedBlockingQueue<Continuation<Unit>>()

    suspend fun waitForSlot() {
        suspendCoroutine<Unit> { continuation ->
            if (currentValue >= maxValue) {
                waitingContinuations.offer(continuation)
            } else {
                currentValue++
                continuation.resume(Unit)
            }
        }
    }

    fun freeSlot() {
        log.verbose("Freeing a slot, currently busy: $currentValue, currently waiting ${waitingContinuations.size}")
        waitingContinuations.poll()?.resume(Unit) ?: currentValue--
    }

    suspend fun withLock(block: suspend (release: () -> Unit) -> Unit) {
        waitForSlot()
        block(::freeSlot)
    }
}