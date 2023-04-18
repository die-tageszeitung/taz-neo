package de.taz.app.android.monkey

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.distinctUntilChanged

fun <T> LiveData<T>.observeDistinctIgnoreFirst(
    lifecycleOwner: LifecycleOwner,
    observationCallback: (T) -> Unit
): Observer<T> {
    val distinctLiveData = distinctUntilChanged()
    val firstTimeObserver = object : Observer<T> {
        var firstTime = true
        override fun onChanged(value: T) {
            if (firstTime) {
                firstTime = false
            } else {
                observationCallback(value)
            }
        }
    }
    distinctLiveData.observe(lifecycleOwner, firstTimeObserver)
    return firstTimeObserver
}