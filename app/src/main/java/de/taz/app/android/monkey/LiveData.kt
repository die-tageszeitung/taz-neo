package de.taz.app.android.monkey

import androidx.lifecycle.*

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

fun <T> LiveData<T>.observeDistinct(
    lifecycleOwner: LifecycleOwner,
    observationCallback: (T) -> Unit
): Observer<T> {
    val observer = Observer(observationCallback)
    distinctUntilChanged().observe(lifecycleOwner, observer)
    return observer
}

fun <T> LiveData<T>.observeDistinct(
    lifecycleOwner: LifecycleOwner,
    observer: Observer<T>
): Observer<T> {
    distinctUntilChanged().observe(lifecycleOwner, observer)
    return observer
}
