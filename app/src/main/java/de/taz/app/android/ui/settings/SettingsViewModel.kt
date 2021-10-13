package de.taz.app.android.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.taz.app.android.*
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.util.SharedPreferenceStorageLocationLiveData
import de.taz.app.android.util.SharedPreferenceStringLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    var fontSizeLiveData: LiveData<String>
    var nightModeLiveData: LiveData<Boolean>

    val downloadOnlyWifiLiveData: LiveData<Boolean>
    val  downloadAutomaticallyLiveData: LiveData<Boolean>

    lateinit var storageLocationLiveData: SharedPreferenceStorageLocationLiveData
    lateinit var storedIssueNumberLiveData: SharedPreferenceStringLiveData

    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(application)
    private val downloadDataStore = DownloadDataStore.getInstance(application)

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

        downloadOnlyWifiLiveData = downloadDataStore.onlyWifi.asLiveData()
        downloadAutomaticallyLiveData = downloadDataStore.enabled.asLiveData()
    }

    fun setOnlyWifi(onlyWifi: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            downloadDataStore.onlyWifi.set(onlyWifi)
        }
    }

    fun setDownloadsEnabled(enabled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            downloadDataStore.enabled.set(enabled)
        }
    }

    fun resetFontSize() {
        CoroutineScope(Dispatchers.IO).launch {
            tazApiCssDataStore.fontSize.reset()
        }
    }

    fun decreaseFontSize() {
        CoroutineScope(Dispatchers.IO).launch {
            val newSize = getFontSize() - 10
            if (newSize >= MIN_TEXT_SIZE) {
                setFontSize(newSize.toString())
            }
        }
    }

    fun increaseFontSize() {
        CoroutineScope(Dispatchers.IO).launch {
            val newSize = getFontSize() + 10
            if (newSize <= MAX_TEST_SIZE) {
                setFontSize(newSize.toString())
            }
        }
    }

    private fun setFontSize(value: String) {
        CoroutineScope(Dispatchers.IO).launch {
            tazApiCssDataStore.fontSize.set(value)
        }
    }

    fun setNightMode(value: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            tazApiCssDataStore.nightMode.set(value)
        }
    }

    private suspend fun getFontSize(): Int = tazApiCssDataStore.fontSize.get().toInt()
}