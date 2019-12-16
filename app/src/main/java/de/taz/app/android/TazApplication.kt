package de.taz.app.android

import android.app.Application
import com.facebook.stetho.Stetho
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory

class TazApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if(BuildConfig.DEBUG) {
           Stetho.initializeWithDefaults(this)
        }
        Sentry.init(AndroidSentryClientFactory(this))
    }

}