package de.taz.app.android.util

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.singletons.*

object NightModeHelper {

    private val log by Log

    class PrefListener(activity: Activity) {
        val tazApiCssPrefListener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                log.debug("Shared pref changed: $key")
                val cssFile = FileHelper.getInstance(activity.application).getFileByPath(
                    "$RESOURCE_FOLDER/tazApi.css"
                )
                val cssString = TazApiCssHelper.generateCssString(sharedPreferences)

                cssFile.writeText(cssString)

                if (key == SETTINGS_TEXT_NIGHT_MODE) {
                    setThemeAndReCreate(sharedPreferences, activity)
                }
            }
    }

    private fun setThemeAndReCreate(
        sharedPreferences: SharedPreferences,
        activity: Activity
    ) {
        if (sharedPreferences.getBoolean(SETTINGS_TEXT_NIGHT_MODE, false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            log.debug("setTheme to NIGHT")
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            log.debug("setTheme to DAY")
        }
        activity.recreate()
    }

    private fun isDarkTheme(activity: Activity): Boolean {
        return activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    fun initializeNightModePrefs(tazApiCssPreferences: SharedPreferences, activity: Activity) {
        // if "text_night_mode" is not set in shared preferences -> set it now
        if (!tazApiCssPreferences.contains(SETTINGS_TEXT_NIGHT_MODE)) {
            SharedPreferenceBooleanLiveData(
                tazApiCssPreferences, SETTINGS_TEXT_NIGHT_MODE, isDarkTheme(activity)
            ).postValue(isDarkTheme(activity))
        }

        if (tazApiCssPreferences.getBoolean(SETTINGS_TEXT_NIGHT_MODE, false) != isDarkTheme(activity)) {
            setThemeAndReCreate(tazApiCssPreferences, activity)
        }

    }
}
