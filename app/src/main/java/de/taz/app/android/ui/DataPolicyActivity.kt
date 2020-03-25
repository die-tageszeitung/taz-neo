package de.taz.app.android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.singletons.SETTINGS_DATA_POLICY_ACCEPTED
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import kotlinx.android.synthetic.main.activity_welcome.*

class DataPolicyActivity : AppCompatActivity() {

    private val log by Log

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        data_policy_fullscreen_content.loadUrl("file:///android_asset/html/data_policy_screen.html")

        findViewById<Button>(R.id.data_privacy_accept_button)
            .setOnClickListener {
                acceptDataPolicy()
                startActivity(Intent(this, MainActivity::class.java))
            }
    }

    private fun acceptDataPolicy() {
        val tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        SharedPreferenceBooleanLiveData(
            tazApiCssPreferences, SETTINGS_DATA_POLICY_ACCEPTED ,true
        ).postValue(true)
    }
}
