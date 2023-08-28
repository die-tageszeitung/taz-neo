package de.taz.app.android.tracking

import android.content.Context
import de.taz.app.android.BuildConfig

/**
 * Non Free Variant implementation of the TrackerFactoryInterface.
 * This might use the actual MatomoTracker in some cases.
 */
class TrackerFactory : TrackerFactoryInterface {
    override fun createInstance(applicationContext: Context): Tracker {
        if (!BuildConfig.IS_LMD) {
            return MatomoTracker(applicationContext)
        } else {
            return NoOpTracker()
        }
    }
}