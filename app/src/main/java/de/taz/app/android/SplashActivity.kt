package de.taz.app.android

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.api.QueryService
import de.taz.app.android.util.AuthHelper

class SplashActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        createSingletons()
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun createSingletons() {
        AuthHelper.createInstance(applicationContext)
        QueryService.createInstance(applicationContext)
    }

}

