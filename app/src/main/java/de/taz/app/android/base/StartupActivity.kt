package de.taz.app.android.base

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.DataPolicyActivity
import de.taz.app.android.ui.START_HOME_ACTIVITY
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.main.MainActivity

@Deprecated(
    message = "To make use of ViewBindings please use ViewBindingActivity or StartupActivity"
)
abstract class StartupActivity : AppCompatActivity() {
    private val generalDataStore by lazy {
        GeneralDataStore.getInstance(applicationContext)
    }

    protected suspend fun startActualApp() {
        if (isDataPolicyAccepted()) {
            if (hasSeenWelcomeScreen()) {
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                intent.putExtra(START_HOME_ACTIVITY, true)
                startActivity(intent)
            } else {
                MainActivity.start(this, Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        } else {
            val intent = Intent(this, DataPolicyActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
        }
    }

    private suspend fun isDataPolicyAccepted(): Boolean =
        generalDataStore.dataPolicyAccepted.get()

    private suspend fun hasSeenWelcomeScreen(): Boolean = !generalDataStore.hasSeenWelcomeScreen.get()
}