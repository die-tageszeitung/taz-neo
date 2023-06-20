package de.taz.app.android

import com.google.firebase.FirebaseApp
import de.taz.app.android.tracking.MatomoTracker
import de.taz.app.android.tracking.NoOpTracker
import de.taz.app.android.tracking.Tracker

@Suppress("UNUSED")
class TazApplication : AbstractTazApplication() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }

    override fun initializeTracker(): Tracker {
        if (BuildConfig.BUILD_TYPE == "debug" && !BuildConfig.IS_LMD) {
            return MatomoTracker(applicationContext)
        } else {
            return NoOpTracker()
        }
    }
}