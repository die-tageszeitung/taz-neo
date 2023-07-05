package de.taz.app.android.tracking

import android.content.Context

/**
 * This interface has to be implemented by all build variant dependent TrackerFactorys.
 */
interface TrackerFactoryInterface {
    fun createInstance(applicationContext: Context): Tracker
}

/**
 * Helper function to be used from the  [de.taz.app.android.util.SingletonHolder] of [Tracker].
 * It will create an Instance of the build variant dependent TrackerFactory implementation
 * and return the created Tracker instance.
 */
fun createTrackerInstance(applicationContext: Context): Tracker {
    val trackerFactory: TrackerFactoryInterface = TrackerFactory()
    return trackerFactory.createInstance(applicationContext)
}