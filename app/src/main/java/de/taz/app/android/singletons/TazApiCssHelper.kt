package de.taz.app.android.singletons

import android.content.SharedPreferences
import de.taz.app.android.api.models.RESOURCE_FOLDER

const val SETTINGS_DATA_POLICY_ACCEPTED = "data_policy_accepted"
const val SETTINGS_FIRST_TIME_APP_STARTS = "first_time_app_starts"
const val DEFAULT_FONT_SIZE = 18
const val SETTINGS_TEXT_NIGHT_MODE = "text_night_mode"
const val SETTINGS_TEXT_FONT_SIZE = "text_font_size"
const val SETTINGS_TEXT_FONT_SIZE_DEFAULT = "100"

object TazApiCssHelper {

    private val fileHelper = FileHelper.getInstance()

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
        val fontSizePx = computeFontSize(
            sharedPreferences.getString(
                SETTINGS_TEXT_FONT_SIZE,
                SETTINGS_TEXT_FONT_SIZE_DEFAULT
            ) ?: SETTINGS_TEXT_FONT_SIZE_DEFAULT
        )
        var backgroundColor = "#fff"

        if (sharedPreferences.getBoolean(SETTINGS_TEXT_NIGHT_MODE, false)) {
            backgroundColor = "#121212"
            return """
                @import "$nightModeCssFile";
                html, body {
                    background-color : ${backgroundColor};
                    font-size        : ${fontSizePx}px;
                }
                div.demoDiv:before {
                    background-image	: --webkit_linear-gradient(0deg,${backgroundColor} 5%,hsla(0,0%,100%,0));
                    background-image	: linear-gradient(0deg,${backgroundColor} 5%,hsla(0,0%,100%,0));
                }""".trimIndent()


        } else {
            return """
                html, body {
                    background-color : ${backgroundColor};
                    font-size        : ${fontSizePx}px;
                }
            """.trimIndent()
        }
    }
}
