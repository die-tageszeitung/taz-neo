package de.taz.app.android.util

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations

fun <T> observe(
    liveData: LiveData<T>,
    lifecycleOwner: LifecycleOwner,
    observationCallback: (T) -> Unit
): Observer<T> {
    val observer = Observer(observationCallback)
    Transformations.distinctUntilChanged(liveData).observe(lifecycleOwner, observer)
    return observer
}

fun <T> observeOnce(
    liveData: LiveData<T>,
    lifecycleOwner: LifecycleOwner,
    observationCallback: (T) -> Unit
): Observer<T> {
    val observer = object : Observer<T> {
        override fun onChanged(t: T) {
            observationCallback(t)
            liveData.removeObserver(this)
        }
    }
    Transformations.distinctUntilChanged(liveData).observe(lifecycleOwner, observer)
    return observer
}