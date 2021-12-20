package de.taz.app.android

import android.app.Application
import android.os.StrictMode
import com.facebook.stetho.Stetho
import com.google.firebase.FirebaseApp

@Suppress("UNUSED")
class TazApplication : AbstractTazApplication() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}