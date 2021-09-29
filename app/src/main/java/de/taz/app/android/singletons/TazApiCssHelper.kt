package de.taz.app.android.singletons

import android.content.Context
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_NIGHT_COLOR = "#121212"
private const val DEFAULT_DAY_COLOR = "#FFFFFF"
private const val DEFAULT_FONT_SIZE = 18

class TazApiCssHelper private constructor(applicationContext: Context){

    companion object: SingletonHolder<TazApiCssHelper, Context>(::TazApiCssHelper)

    private val storageService = StorageService.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(applicationContext)

    /**
     * Computes an actual font size using the default font size and the display percentage
     * entered by the user
     */
    private fun computeFontSize(percentage: String): String {
        val fontSize = percentage.toInt() * 0.01 * DEFAULT_FONT_SIZE
        return fontSize.toString()
    }

    suspend fun generateCssString(): String =
        withContext(Dispatchers.IO) {
            val nightModeCSSFileEntry = fileEntryRepository.get("themeNight.css")
            val nightModeCssFile = nightModeCSSFileEntry?.let {
                storageService.getFile(nightModeCSSFileEntry)
            }
            val importString = nightModeCssFile?.let { "@import \"${it.absolutePath}\";" } ?: ""

            val fontSizePx = computeFontSize(tazApiCssDataStore.fontSize.current())

            if (tazApiCssDataStore.nightMode.current()) {
                return@withContext """
                            $importString
                            html, body {
                                background-color : ${DEFAULT_NIGHT_COLOR};
                                font-size        : ${fontSizePx}px;
                            }
                            div.demoDiv:before {
                                background-image : --webkit_linear-gradient(0deg,${DEFAULT_NIGHT_COLOR} 5%,hsla(0,0%,100%,0));
                                background-image : linear-gradient(0deg,${DEFAULT_NIGHT_COLOR} 5%,hsla(0,0%,100%,0));
                            }""".trimIndent()
            } else {
                return@withContext """
                            html, body {
                                background-color : ${DEFAULT_DAY_COLOR};
                                font-size        : ${fontSizePx}px;
                            }""".trimIndent()
            }
        }
}

