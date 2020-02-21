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