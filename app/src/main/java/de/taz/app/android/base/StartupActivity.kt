package de.taz.app.android.base

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.TazApplication
import de.taz.app.android.ui.main.MainActivity

abstract class StartupActivity : AppCompatActivity() {

    protected fun startActualApp() {
        MainActivity.start(this, Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    protected val applicationScope by lazy {
        (application as TazApplication).applicationScope
    }
}