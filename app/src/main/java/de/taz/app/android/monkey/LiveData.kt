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
    val firstTimeObserver = object : Observer<T> {
        var firstTime = true
        override fun onChanged(t: T) {
            if (firstTime) {
                firstTime = false
            } else {
                observationCallback(t)
            }
        }
    }
    distinctLiveData.observe(lifecycleOwner, firstTimeObserver)
    return firstTimeObserver
}

fun <T> LiveData<T>.observeDistinct(
    lifecycleOwner: LifecycleOwner,
    observationCallback: (T) -> Unit
): Observer<T> {
    val observer = Observer(observationCallback)
    Transformations.distinctUntilChanged(this).observe(lifecycleOwner, observer)
    return observer
}

fun <T> LiveData<T>.observeDistinct(
    lifecycleOwner: LifecycleOwner,
    observer: Observer<T>
): Observer<T> {
    Transformations.distinctUntilChanged(this).observe(lifecycleOwner, observer)
    return observer
}

fun <T> LiveData<T>.observeDistinctOnce(
    lifecycleOwner: LifecycleOwner,
    observationCallback: (T) -> Unit
): Observer<T> {
    val liveData = Transformations.distinctUntilChanged(this)
    val observer = object : Observer<T> {
        override fun onChanged(t: T) {
            liveData.removeObserver(this)
            observationCallback(t)
        }
    }
    liveData.observe(lifecycleOwner, observer)
    return observer
}
fun <T> LiveData<T>.observeUntil(
    lifecycleOwner: LifecycleOwner,
    observationCallback: (T) -> Unit,
    untilFunction: (T) -> Boolean
): Observer<T> {
    val observer = object : Observer<T> {
        override fun onChanged(t: T) {
            observationCallback(t)
            if (untilFunction(t)) {
                this@observeUntil.removeObserver(this)
            }
        }
    }
    this.observe(lifecycleOwner, observer)
    return observer
}

fun <T> LiveData<T>.observeDistinctUntil(
    lifecycleOwner: LifecycleOwner,
    observationCallback: (T) -> Unit,
    untilFunction: (T) -> Boolean
): Observer<T> {
    val liveData = Transformations.distinctUntilChanged(this)
    val observer = object : Observer<T> {
        override fun onChanged(t: T) {
            observationCallback(t)
            if (untilFunction(t)) {
                liveData.removeObserver(this)
            }
        }
    }
    liveData.observe(lifecycleOwner, observer)
    return observer
}
