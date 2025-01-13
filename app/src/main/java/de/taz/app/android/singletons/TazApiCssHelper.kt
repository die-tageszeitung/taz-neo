package de.taz.app.android.singletons

import android.content.Context
import de.taz.app.android.R
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_NIGHT_COLOR = "#121212"
private const val DEFAULT_DAY_COLOR = "#FFFFFF"
private const val DEFAULT_FONT_SIZE = 18
const val DEFAULT_COLUMN_GAP_PX = 34f

class TazApiCssHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<TazApiCssHelper, Context>(::TazApiCssHelper)

    private val storageService = StorageService.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(applicationContext)
    private val resources = applicationContext.resources

    /**
     * Computes an actual font size using the default font size and the display percentage
     * entered by the user
     */
    private fun computeFontSize(percentage: String): String {
        val fontSize = percentage.toInt() * 0.01 * DEFAULT_FONT_SIZE
        return fontSize.toString()
    }

    private val articleMarginTop =
        resources.getDimension(R.dimen.fragment_webview_article_margin_top) / resources.displayMetrics.density
    private val articleLittleMarginTop =
        resources.getDimension(R.dimen.fragment_webview_article_little_margin_top) / resources.displayMetrics.density
    private val sectionMarginTop =
        resources.getDimension(R.dimen.fragment_webview_section_margin_top) / resources.displayMetrics.density
    private val articlePaddingBottom =
        resources.getDimension(R.dimen.fragment_article_padding_bottom) / resources.displayMetrics.density


    suspend fun generateCssString(): String =
        withContext(Dispatchers.IO) {
            val nightModeCSSFileEntry = fileEntryRepository.get("themeNight.css")
            val nightModeCssFile = nightModeCSSFileEntry?.let {
                storageService.getFile(nightModeCSSFileEntry)
            }
            val importString = nightModeCssFile?.let { "@import \"${it.absolutePath}\";" } ?: ""

            val fontSizePx = computeFontSize(tazApiCssDataStore.fontSize.get())
            val textAlign = if (tazApiCssDataStore.textJustification.get()) "justify" else "left"

            // The column-width, #content.width and height will be calculated dependent on the
            // actual View size. See tazApiJs.enableArticleColumnMode for further information.
            // The ::-webkit-scrollbar must be disabled to make sure that high content like portrait
            // images won't break the column calculation. By default WebViews reserve a space of 6px
            // for scrollbars, but we can hide it as we don't use the scrollbars at all.
            val multiColumnsString = """
                #content.article--multi-column {
                    column-fill: auto;
                    column-gap: ${DEFAULT_COLUMN_GAP_PX}px;
                    orphans: 3;
                    widows: 3;
                    margin-left: 0px;
                    padding-left: ${DEFAULT_COLUMN_GAP_PX}px;
                    padding-right: ${DEFAULT_COLUMN_GAP_PX}px;
                    left: 0px;
                    overflow-x: scroll;
                    height: 100%;
                    padding-top: ${articleLittleMarginTop}px;
                    padding-bottom: 0px;
                }
                #content.article--multi-column::-webkit-scrollbar {
                    display: none;
                }
                .no-horizontal-padding {
                    padding-left: 0px;
                    padding-right: 0px;
                }
            """.trimIndent()

            val default = """
                html, body {
                    font-size        : ${fontSizePx}px;
                }
                #content.article {
                    padding-top: ${articleMarginTop}px;
                    padding-bottom: ${articlePaddingBottom}px;
                }
                #content.section {
                    padding-top: ${sectionMarginTop}px;
                }
                p {
                    text-align: ${textAlign};
                }
                $multiColumnsString
            """.trimIndent()

            if (tazApiCssDataStore.nightMode.get()) {
                """
                    $importString
                    html, body {
                        background-color : ${DEFAULT_NIGHT_COLOR};
                    }
                    div.demoDiv:before {
                        background-image : --webkit_linear-gradient(0deg,${DEFAULT_NIGHT_COLOR} 5%,hsla(0,0%,100%,0));
                        background-image : linear-gradient(0deg,${DEFAULT_NIGHT_COLOR} 5%,hsla(0,0%,100%,0));
                    }
                    $default
                    """.trimIndent()
            } else {
                """
                    $default
                    html, body {
                        background-color : ${DEFAULT_DAY_COLOR};
                    }
                """.trimIndent()
            }
        }
}

