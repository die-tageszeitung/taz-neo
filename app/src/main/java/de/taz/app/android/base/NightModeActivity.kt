package de.taz.app.android.base

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.util.NightModeHelper

abstract class NightModeActivity(layoutId: Int? = null): BaseActivity(layoutId) {

    private lateinit var tazApiCssPreferences: SharedPreferences

    private lateinit var tazApiCssPrefListener : SharedPreferences.OnSharedPreferenceChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        NightModeHelper.initializeNightModePrefs(tazApiCssPreferences, this)

        tazApiCssPrefListener = NightModeHelper.PrefListener(this).tazApiCssPrefListener
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