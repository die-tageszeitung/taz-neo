package de.taz.app.android.util

import android.content.SharedPreferences
import de.taz.app.android.api.models.RESOURCE_FOLDER

const val SETTINGS_TEXT_DEFAULT_FONT_SIZE = 18

object TazApiCssHelper {

    private val fileHelper = FileHelper.getInstance()

    /**
     * Computes an actual font size using the default font size and the display percentage
     * entered by the user
     */
    private fun computeFontSize(percentage: String) : String {
        val fontSize = percentage.toInt() * 0.01 * SETTINGS_TEXT_DEFAULT_FONT_SIZE
        return fontSize.toString()
    }

    fun generateCssString(sharedPreferences: SharedPreferences) : String {
        val nightModeCssFile = fileHelper.getFile("$RESOURCE_FOLDER/themeNight.css")
        val nightModeCssString = if (sharedPreferences.getBoolean("text_night_mode", false)) "@import \"$nightModeCssFile\";" else ""
        val fontSizePx = computeFontSize(sharedPreferences.getString("text_font_size", "100") ?: "100")
        return """
            $nightModeCssString
            html, body {
                font-size: ${fontSizePx}px;
            }
        """.trimIndent()
    }
}
