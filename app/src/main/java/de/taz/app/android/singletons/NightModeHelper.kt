package de.taz.app.android.singletons

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NightModeHelper private constructor(private val applicationContext: Context) {
    companion object : SingletonHolder<NightModeHelper, Context>(::NightModeHelper)

    private val log by Log

    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(applicationContext)

    init {
        launch {
            tazApiCssDataStore.nightMode.asFlow().collect {
                generateCssOverride()
                setNightMode(it)
            }
        }
    }

    private suspend fun generateCssOverride() = withContext(Dispatchers.IO) {
        val cssFileEntry =
            FileEntryRepository.getInstance(applicationContext).get("tazApi.css")

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
