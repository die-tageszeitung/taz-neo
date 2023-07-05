package de.taz.app.android.tracking

import android.content.Context

/**
 * Free Variant implementation of the TrackerFactoryInterface.
 * It will always use the No Operation fake tracker.
 */
class TrackerFactory : TrackerFactoryInterface {
    override fun createInstance(applicationContext: Context): Tracker {
        return NoOpTracker()
    }
}