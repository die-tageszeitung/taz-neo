package de.taz.app.android.uiSynchronization

import androidx.test.espresso.idling.CountingIdlingResource

const val IDLING_RESOURCE_INITIALIZATION = "IDLING_RESOURCE_INITIALIZATION"
const val IDLING_RESOURCE_MOMENT_DOWNLOAD = "IDLING_RESOURCE_MOMENT_DOWNLOAD"

fun CountingIdlingResource.decrementIfNotIdle() {
    if (!this.isIdleNow) {
        this.decrement()
    }
}

val InitializationResource = CountingIdlingResource(IDLING_RESOURCE_INITIALIZATION)
val DownloadResource = CountingIdlingResource(IDLING_RESOURCE_MOMENT_DOWNLOAD)