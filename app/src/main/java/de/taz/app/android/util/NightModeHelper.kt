package de.taz.app.android.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object NightModeHelper {

    private val log by Log

    class PrefListener(activity: Activity) {
        val tazApiCssPrefListener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                log.debug("Shared pref changed: $key")
                CoroutineScope(Dispatchers.IO).launch {
                    if (key == SETTINGS_TEXT_NIGHT_MODE) {
                        generateCssOverride(activity)
                        withContext(Dispatchers.Main) {
                            setThemeAndReCreate(sharedPreferences, activity)
                        }
                    }
                }
            }
    }

    suspend fun generateCssOverride(activity: Activity) {
        val cssSharedPreferences = activity.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)
        val cssFileEntry =
            FileEntryRepository.getInstance(activity.application).get("tazApi.css")

        cssFileEntry?.let {
            val cssFile = StorageService.getInstance(activity.application).getFile(it)
            val cssString = TazApiCssHelper.generateCssString(activity, cssSharedPreferences)
            cssFile?.writeText(cssString)
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

        if (tazApiCssPreferences.getBoolean(
                SETTINGS_TEXT_NIGHT_MODE,
                false
            ) != isDarkTheme(activity)
        ) {
            setThemeAndReCreate(tazApiCssPreferences, activity)
        }

    }
}
