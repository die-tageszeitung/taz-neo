package de.taz.app.android.monkey

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations

fun <T> LiveData<T>.observeDistinctIgnoreFirst(
    lifecycleOwner: LifecycleOwner,
    observationCallback: (T) -> Unit
): Observer<T> {
    val distinctLiveData = Transformations.distinctUntilChanged(this)
    val observer = Observer(observationCallback)
    val firstTimeObserver = object : Observer<T> {
        var firstTime = true
        override fun onChanged(t: T) {
            if (firstTime) {
                firstTime = false
            } else {
                removeObserver(this)
                distinctLiveData.observe(lifecycleOwner, observer)
            }
        }
    }
    distinctLiveData.observe(lifecycleOwner, firstTimeObserver)
    return observer
}

fun <T> LiveData<T>.observeDistinct(
    lifecycleOwner: LifecycleOwner,
    observationCallback: (T) -> Unit
): Observer<T> {
    val observer = Observer(observationCallback)
    Transformations.distinctUntilChanged(this).observe(lifecycleOwner, observer)
    return observer
}

fun <T> LiveData<T>.observeDistinctOnce(
    lifecycleOwner: LifecycleOwner,
    observationCallback: (T) -> Unit
): Observer<T> {
    val observer = object : Observer<T> {
        override fun onChanged(t: T) {
            observationCallback(t)
            removeObserver(this)
        }
    }
    Transformations.distinctUntilChanged(this).observe(lifecycleOwner, observer)
    return observer
}