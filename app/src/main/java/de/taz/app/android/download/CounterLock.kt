package de.taz.app.android.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class CounterLock(maxValue: Int) {
    private val semaphore = Semaphore(maxValue)


    suspend fun dispatchWithLock(block: suspend () -> Unit) = CoroutineScope(Dispatchers.IO).launch {
        semaphore.withPermit {
            block()
        }
    }
}