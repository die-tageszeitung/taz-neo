package de.taz.app.android.base

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.QueryService
import de.taz.app.android.data.DataService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.singletons.*
import de.taz.app.android.util.Log
import de.taz.app.android.singletons.SubscriptionPollHelper
import de.taz.app.android.ui.DataPolicyActivity
import de.taz.app.android.ui.START_HOME_ACTIVITY
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.main.MainActivity

abstract class BaseActivity(private val layoutId: Int? = null) : AppCompatActivity() {

    private val log by Log

    protected val generalDataStore by lazy {
        GeneralDataStore.getInstance(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createSingletons()
        layoutId?.let { setContentView(layoutId) }
    }

    private fun createSingletons() {
        log.info("creating singletons")
        applicationContext.let {
            AppDatabase.createInstance(it)

            AuthHelper.createInstance(it)
            StorageService.createInstance(it)
            NotificationHelper.createInstance(it)
            SubscriptionPollHelper.createInstance(it)
            ToastHelper.createInstance(it)

            QueryService.createInstance(it)
            ToastHelper.createInstance(it)

            ApiService.createInstance(it)

            FirebaseHelper.createInstance(it)
            DataService.createInstance(it)
        }
        log.debug("Singletons initialized")
    }

    protected suspend fun startActualApp() {
        if (isDataPolicyAccepted()) {
            if (hasSeenWelcomeScreen()) {
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                intent.putExtra(START_HOME_ACTIVITY, true)
                startActivity(intent)
            } else {
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                startActivity(intent)
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