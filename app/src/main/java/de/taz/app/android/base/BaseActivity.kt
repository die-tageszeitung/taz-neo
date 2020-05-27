package de.taz.app.android.base

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.NightModeHelper

abstract class BaseActivity(layoutID: Int): AppCompatActivity(layoutID) {

    //override fun getLifecycleOwner(): LifecycleOwner = this

    //override fun getMainView(): MainActivity? = null
    private val log by Log

    private lateinit var tazApiCssPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        NightModeHelper.initializeNightModePrefs(tazApiCssPreferences, this)

        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        tazApiCssPreferences.registerOnSharedPreferenceChangeListener(NightModeHelper.tazApiCssPrefListener)
    }

    override fun onPause() {
        super.onPause()
        tazApiCssPreferences.unregisterOnSharedPreferenceChangeListener(NightModeHelper.tazApiCssPrefListener)
    }

}