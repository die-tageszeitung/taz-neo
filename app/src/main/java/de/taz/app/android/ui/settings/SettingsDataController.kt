package de.taz.app.android.ui.settings

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.PREFERENCES_DOWNLOADS
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.singletons.*
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SharedPreferenceStringLiveData
import de.taz.app.android.monkey.observeDistinct

class SettingsDataController : BaseDataController(), SettingsContract.DataController {

    private lateinit var textSizeLiveData: SharedPreferenceStringLiveData
    private lateinit var nightModeLiveData: SharedPreferenceBooleanLiveData
    private lateinit var storedIssueNumberLiveData: SharedPreferenceStringLiveData
    private lateinit var downloadOnlyWifiLiveData: SharedPreferenceBooleanLiveData

    override fun observeNightMode(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (Boolean) -> Unit
    ) {
        nightModeLiveData.observeDistinct(lifecycleOwner, observationCallback)
    }

    override fun observeTextSize(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (String) -> Unit
    ) {
        textSizeLiveData.observeDistinct(lifecycleOwner, observationCallback)
    }

    override fun observeStoredIssueNumber(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (String) -> Unit
    ) {
        storedIssueNumberLiveData.observeDistinct(lifecycleOwner, observationCallback)
    }

    override fun observeDownloadOnlyInWifi(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (Boolean) -> Unit
    ) {
        downloadOnlyWifiLiveData.observeDistinct(lifecycleOwner, observationCallback)
    }

    override fun setStoredIssueNumber(number: Int) {
        storedIssueNumberLiveData.postValue(number.toString())
    }

    override fun setNightMode(activated: Boolean) {
        nightModeLiveData.postValue(activated)
    }

    override fun setTextSizePercent(percent: String) {
        textSizeLiveData.postValue(percent)
    }

    override fun getTextSizePercent(): String {
        return textSizeLiveData.value ?: SETTINGS_TEXT_FONT_SIZE_DEFAULT
    }

    override fun setDownloadOnlyInWifi(onlyWifi: Boolean) {
        downloadOnlyWifiLiveData.postValue(onlyWifi)
    }

    override fun initializeSettings(applicationContext: Context) {
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