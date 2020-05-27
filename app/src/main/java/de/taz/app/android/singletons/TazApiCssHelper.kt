package de.taz.app.android.singletons

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.util.Log
import de.taz.app.android.util.NightModeHelper
import de.taz.app.android.util.SharedPreferenceBooleanLiveData

const val SETTINGS_DATA_POLICY_ACCEPTED = "data_policy_accepted"
const val SETTINGS_FIRST_TIME_APP_STARTS = "first_time_app_starts"
const val DEFAULT_FONT_SIZE = 18
const val SETTINGS_TEXT_NIGHT_MODE = "text_night_mode"
const val SETTINGS_TEXT_FONT_SIZE = "text_font_size"
const val SETTINGS_TEXT_FONT_SIZE_DEFAULT = "100"

object TazApiCssHelper {

    private val fileHelper = FileHelper.getInstance()
    private val log by Log

    /**
     * Computes an actual font size using the default font size and the display percentage
     * entered by the user
     */
    private fun computeFontSize(percentage: String) : String {
        val fontSize = percentage.toInt() * 0.01 * DEFAULT_FONT_SIZE
        return fontSize.toString()
    }

    fun generateCssString(sharedPreferences: SharedPreferences) : String {
        val nightModeCssFile = fileHelper.getFileByPath("$RESOURCE_FOLDER/themeNight.css")
        val nightModeCssString = if (sharedPreferences.getBoolean(SETTINGS_TEXT_NIGHT_MODE, false)) "@import \"$nightModeCssFile\";" else ""
        val fontSizePx = computeFontSize(sharedPreferences.getString(SETTINGS_TEXT_FONT_SIZE,  SETTINGS_TEXT_FONT_SIZE_DEFAULT) ?: SETTINGS_TEXT_FONT_SIZE_DEFAULT)
        return """
            $nightModeCssString
            html, body {
                font-size: ${fontSizePx}px;
            }
        """.trimIndent()
    }



    val tazApiCssPrefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
           log.debug("Shared pref changed: $key")
            val cssFile = fileHelper.getFileByPath(
                "$RESOURCE_FOLDER/tazApi.css"
            )
            val cssString = generateCssString(sharedPreferences)

            cssFile.writeText(cssString)

            if (key == SETTINGS_TEXT_NIGHT_MODE) {
                setThemeAndReCreate(sharedPreferences, activity, true)
            }
        }

    private fun setThemeAndReCreate(
        sharedPreferences: SharedPreferences,
        activity: Activity,
        isReCreateFlagSet: Boolean = false
    ) {
        if (sharedPreferences.getBoolean(SETTINGS_TEXT_NIGHT_MODE, false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            log.debug("setTheme to NIGHT")
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            log.debug("setTheme to DAY")
        }
        if (isReCreateFlagSet) {
            activity.recreate()
        }
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
            setThemeAndReCreate(tazApiCssPreferences, activity, false)
        }

    }
}
