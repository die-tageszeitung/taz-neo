package de.taz.app.android.singletons

import android.content.Context
import android.content.SharedPreferences
import de.taz.app.android.R
import de.taz.app.android.persistence.repository.FileEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val SETTINGS_DATA_POLICY_ACCEPTED = "data_policy_accepted"
const val SETTINGS_FIRST_TIME_APP_STARTS = "first_time_app_starts"
const val DEFAULT_FONT_SIZE = 18
const val SETTINGS_TEXT_NIGHT_MODE = "text_night_mode"
const val SETTINGS_TEXT_FONT_SIZE = "text_font_size"
const val SETTINGS_DEFAULT_NIGHT_COLOR = "#121212"
const val SETTINGS_DEFAULT_DAY_COLOR = "#FFFFFF"
const val SETTINGS_TEXT_FONT_SIZE_FALLBACK = 100

object TazApiCssHelper {

    private val storageService = StorageService.getInstance()
    private val fileEntryRepository = FileEntryRepository.getInstance()

    /**
     * Computes an actual font size using the default font size and the display percentage
     * entered by the user
     */
    private fun computeFontSize(percentage: String): String {
        val fontSize = percentage.toInt() * 0.01 * DEFAULT_FONT_SIZE
        return fontSize.toString()
    }

    suspend fun generateCssString(context: Context, sharedPreferences: SharedPreferences): String =
        withContext(Dispatchers.IO) {
            val nightModeCSSFileEntry = fileEntryRepository.get("themeNight.css")
            val nightModeCssFile = nightModeCSSFileEntry?.let {
                storageService.getFile(nightModeCSSFileEntry)
            }
            val importString = nightModeCssFile?.let { "@import \"${it.absolutePath}\";" } ?: ""
            val defaultFontSize = context.resources.getInteger(R.integer.text_default_size)

            val fontSizePx = computeFontSize(
                // TODO: Why is that a string, should migrate
                sharedPreferences.getString(
                    SETTINGS_TEXT_FONT_SIZE,
                    defaultFontSize.toString()
                ) ?: defaultFontSize.toString()
            )

            if (sharedPreferences.getBoolean(SETTINGS_TEXT_NIGHT_MODE, false)) {
                return@withContext """
                            $importString
                            html, body {
                                background-color : ${SETTINGS_DEFAULT_NIGHT_COLOR};
                                font-size        : ${fontSizePx}px;
                            }
                            div.demoDiv:before {
                                background-image : --webkit_linear-gradient(0deg,${SETTINGS_DEFAULT_NIGHT_COLOR} 5%,hsla(0,0%,100%,0));
                                background-image : linear-gradient(0deg,${SETTINGS_DEFAULT_NIGHT_COLOR} 5%,hsla(0,0%,100%,0));
                            }""".trimIndent()
            } else {
                return@withContext """
                            html, body {
                                background-color : ${SETTINGS_DEFAULT_DAY_COLOR};
                                font-size        : ${fontSizePx}px;
                            }""".trimIndent()
            }
        }
}

