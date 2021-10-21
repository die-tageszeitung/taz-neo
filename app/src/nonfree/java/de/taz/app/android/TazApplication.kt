package de.taz.app.android

import android.app.Application
import android.os.StrictMode
import com.facebook.stetho.Stetho
import com.google.firebase.FirebaseApp

@Suppress("UNUSED")
class TazApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this)
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .penaltyLog()
                    .build()
            )
        }
        SentryProvider.initSentry(this)
        FirebaseApp.initializeApp(this)

    }
}