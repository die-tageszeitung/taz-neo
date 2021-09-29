package de.taz.app.android.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.taz.app.android.*
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.ui.bottomSheet.textSettings.MAX_TEST_SIZE
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SharedPreferenceStorageLocationLiveData
import de.taz.app.android.util.SharedPreferenceStringLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    var fontSizeLiveData: LiveData<String>
    var nightModeLiveData: LiveData<Boolean>
    lateinit var storedIssueNumberLiveData: SharedPreferenceStringLiveData
    lateinit var downloadOnlyWifiLiveData: SharedPreferenceBooleanLiveData
    lateinit var downloadAutomaticallyLiveData: SharedPreferenceBooleanLiveData
    lateinit var storageLocationLiveData: SharedPreferenceStorageLocationLiveData

    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(application)

    init {
        fontSizeLiveData = tazApiCssDataStore.fontSize.asLiveData()

        nightModeLiveData = tazApiCssDataStore.nightMode.asLiveData()

        application.getSharedPreferences(PREFERENCES_GENERAL, Context.MODE_PRIVATE)?.let {
            storedIssueNumberLiveData =
                SharedPreferenceStringLiveData(
                    it,
                    SETTINGS_GENERAL_KEEP_ISSUES,
                    SETTINGS_GENERAL_KEEP_ISSUES_DEFAULT.toString()
                )
            storageLocationLiveData =
                SharedPreferenceStorageLocationLiveData(
                    it,
                    SETTINGS_GENERAL_STORAGE_LOCATION,
                    SETTINGS_GENERAL_STORAGE_LOCATION_DEFAULT
                )
        }

        application.getSharedPreferences(PREFERENCES_DOWNLOADS, Context.MODE_PRIVATE)?.let {
            downloadOnlyWifiLiveData =
                SharedPreferenceBooleanLiveData(
                    it,
                    SETTINGS_DOWNLOAD_ONLY_WIFI,
                    true
                )

            downloadAutomaticallyLiveData =
                SharedPreferenceBooleanLiveData(
                    it,
                    SETTINGS_DOWNLOAD_ENABLED,
                    true
                )
        }
    }

    fun resetFontSize() = CoroutineScope(Dispatchers.IO).launch {
        tazApiCssDataStore.fontSize.reset()
    }

    fun decreaseFontSize() = CoroutineScope(Dispatchers.IO).launch {
        val newSize = getFontSize() - 10
        if (newSize <= MAX_TEST_SIZE) {
            updateFontSize(newSize.toString())
        }
    }

    fun increaseFontSize() = CoroutineScope(Dispatchers.IO).launch {
        val newSize = getFontSize() + 10
        if (newSize <= MAX_TEST_SIZE) {
            updateFontSize(newSize.toString())
        }
    }

    private fun updateFontSize(value: String) = CoroutineScope(Dispatchers.IO).launch {
        tazApiCssDataStore.fontSize.update(value)
    }

    fun updateNightMode(value: Boolean) = CoroutineScope(Dispatchers.IO).launch {
        tazApiCssDataStore.nightMode.update(value)
    }

    private suspend fun getFontSize(): Int = tazApiCssDataStore.fontSize.current().toInt()
}