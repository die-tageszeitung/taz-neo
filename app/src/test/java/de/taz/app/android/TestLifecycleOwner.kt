package de.taz.app.android

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScope

class TestLifecycleOwner(val testCoroutineScope: CoroutineScope = TestCoroutineScope()) : LifecycleOwner {

    private val lifecycle = LifecycleRegistry(this)

    init {
        onStart()
    }

    fun onCreate() {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun getLifecycleScope(): CoroutineScope {
        return testCoroutineScope
    }


    // Other lifecycle callback methods
    override fun getLifecycle() = lifecycle
}