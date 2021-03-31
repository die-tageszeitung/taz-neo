package de.taz.app.android.base

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.singletons.SETTINGS_TEXT_NIGHT_MODE
import de.taz.app.android.util.NightModeHelper

abstract class NightModeActivity(layoutId: Int? = null): BaseActivity(layoutId) {

    private lateinit var tazApiCssPreferences: SharedPreferences

    private lateinit var tazApiCssPrefListener : SharedPreferences.OnSharedPreferenceChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        // Set initial theme by reading the preferences
        val darkModeEnabled = tazApiCssPreferences.getBoolean(SETTINGS_TEXT_NIGHT_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkModeEnabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

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