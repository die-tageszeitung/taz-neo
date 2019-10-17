package de.taz.app.android

import android.app.Application
import com.facebook.stetho.Stetho

class TazApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if(BuildConfig.DEBUG) {
           Stetho.initializeWithDefaults(this)
        }
    }

}