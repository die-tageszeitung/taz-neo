package de.taz.app.android.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NightModeHelper {

    private val log by Log

    suspend fun generateCssOverride(applicationContext: Context)  = withContext(Dispatchers.IO) {
        val cssFileEntry =
            FileEntryRepository.getInstance(applicationContext).get("tazApi.css")

        cssFileEntry?.let {
            val cssFile = StorageService.getInstance(applicationContext).getFile(it)
            val cssString = TazApiCssHelper.getInstance(applicationContext).generateCssString()
            cssFile?.writeText(cssString)
        }
    }

    fun setThemeAndReCreate(
        activity: NightModeActivity,
        nightMode: Boolean
    ) {
        if (nightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            log.debug("setTheme to NIGHT")
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            log.debug("setTheme to DAY")
        }
        activity.recreate()
    }

    fun isDarkTheme(activity: Activity): Boolean {
        return activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

}
