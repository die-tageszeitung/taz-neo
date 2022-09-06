package de.taz.app.android.base

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.TazApplication
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.DataPolicyActivity
import de.taz.app.android.ui.START_HOME_ACTIVITY
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.main.MainActivity

abstract class StartupActivity : AppCompatActivity() {

    protected suspend fun startActualApp() {
        val generalDataStore = GeneralDataStore.getInstance(applicationContext)
        val isDataPolicyAccepted = generalDataStore.dataPolicyAccepted.get()
        val hasSeenWelcomeScreen = generalDataStore.hasSeenWelcomeScreen.get()

        when {
            isDataPolicyAccepted && hasSeenWelcomeScreen ->
                MainActivity.start(this, Intent.FLAG_ACTIVITY_NO_ANIMATION)

            isDataPolicyAccepted && !hasSeenWelcomeScreen ->
                Intent(this, WelcomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra(START_HOME_ACTIVITY, true)
                    startActivity(this)
                }

            else ->
                Intent(this, DataPolicyActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    startActivity(this)
                }
        }
    }

    protected val applicationScope by lazy {
        (application as TazApplication).applicationScope
    }
}