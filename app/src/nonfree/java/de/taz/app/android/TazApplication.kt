package de.taz.app.android

import com.google.firebase.FirebaseApp

@Suppress("UNUSED")
class TazApplication : AbstractTazApplication() {
    var elapsedPopupAlreadyShown = false
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}