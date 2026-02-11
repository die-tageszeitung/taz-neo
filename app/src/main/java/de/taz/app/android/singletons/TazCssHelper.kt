package de.taz.app.android.singletons

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import de.taz.app.android.TAZ_API_CSS_FILENAME
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class TazCssHelper private constructor(private val applicationContext: Context) {
    companion object : SingletonHolder<TazCssHelper, Context>(::TazCssHelper)

    private val log by Log

    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(applicationContext)

    init {
        tazApiCssDataStore.nightMode.asFlow().onEach {
            setNightMode(it)
        }.launchIn(CoroutineScope(Dispatchers.Default))

        tazApiCssDataStore.regenerateCssFlow.onEach {
            generateCssOverride()
        }.launchIn(CoroutineScope(Dispatchers.Default))
    }

    suspend fun notifyTazApiCSSFileReady() {
        generateCssOverride()
    }

    private suspend fun generateCssOverride() = withContext(Dispatchers.IO) {
        log.debug("generating CSS override")
        val cssFileEntry =
            FileEntryRepository.getInstance(applicationContext).get(TAZ_API_CSS_FILENAME)

        cssFileEntry?.let {
            val cssFile = StorageService.getInstance(applicationContext).getFile(it)
            val cssString = TazApiCssHelper.getInstance(applicationContext).generateCssString()
            cssFile?.writeText(cssString)
        }
    }

    private suspend fun setNightMode(
        nightMode: Boolean
    ) = withContext(Dispatchers.Main) {
        if (nightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            log.debug("setTheme to NIGHT")
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            log.debug("setTheme to DAY")
        }
    }
}
