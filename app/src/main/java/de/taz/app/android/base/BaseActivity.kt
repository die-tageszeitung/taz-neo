package de.taz.app.android.base

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.util.Log
import de.taz.app.android.util.NightModeHelper

abstract class BaseActivity(layoutID: Int): AppCompatActivity(layoutID) {

    private val log by Log

    private lateinit var tazApiCssPreferences: SharedPreferences

    private lateinit var tazApiCssPrefListener : SharedPreferences.OnSharedPreferenceChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        NightModeHelper.initializeNightModePrefs(tazApiCssPreferences, this)

        tazApiCssPrefListener = NightModeHelper.PrefListener(this).tazApiCssPrefListener

        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        tazApiCssPreferences.registerOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }

    override fun onPause() {
        super.onPause()
        tazApiCssPreferences.unregisterOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }

}