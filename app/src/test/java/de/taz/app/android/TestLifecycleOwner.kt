package de.taz.app.android

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

class TestLifecycleOwner : LifecycleOwner {

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

    // Other lifecycle callback methods

    override fun getLifecycle() = lifecycle
}