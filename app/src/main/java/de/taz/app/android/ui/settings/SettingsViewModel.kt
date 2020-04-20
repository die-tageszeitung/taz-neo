package de.taz.app.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import de.taz.app.android.PREFERENCES_DOWNLOADS
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.singletons.*
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SharedPreferenceStringLiveData

class SettingsViewModel(applicationContext: Context) : ViewModel() {

    lateinit var textSizeLiveData: SharedPreferenceStringLiveData
    lateinit var nightModeLiveData: SharedPreferenceBooleanLiveData
    lateinit var storedIssueNumberLiveData: SharedPreferenceStringLiveData
    lateinit var downloadOnlyWifiLiveData: SharedPreferenceBooleanLiveData

    init {
        applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)?.let {

            textSizeLiveData =
                SharedPreferenceStringLiveData(
                    it,
                    SETTINGS_TEXT_FONT_SIZE,
                    SETTINGS_TEXT_FONT_SIZE_DEFAULT
                )
            nightModeLiveData =
                SharedPreferenceBooleanLiveData(it, SETTINGS_TEXT_NIGHT_MODE, false)
        }

        applicationContext.getSharedPreferences(PREFERENCES_GENERAL, Context.MODE_PRIVATE)?.let {
            storedIssueNumberLiveData =
                SharedPreferenceStringLiveData(
                    it,
                    SETTINGS_GENERAL_KEEP_ISSUES,
                    SETTINGS_GENERAL_KEEP_ISSUES_DEFAULT.toString()
                )
        }

        applicationContext.getSharedPreferences(PREFERENCES_DOWNLOADS, Context.MODE_PRIVATE)?.let {
            downloadOnlyWifiLiveData =
                SharedPreferenceBooleanLiveData(
                    it,
                    SETTINGS_DOWNLOAD_ONLY_WIFI,
                    true
                )
        }
    }
}