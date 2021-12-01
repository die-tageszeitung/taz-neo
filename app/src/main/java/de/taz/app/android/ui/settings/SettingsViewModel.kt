package de.taz.app.android.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.taz.app.android.*
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    var fontSizeLiveData: LiveData<String>
    var nightModeLiveData: LiveData<Boolean>

    val downloadOnlyWifiLiveData: LiveData<Boolean>
    val  downloadAutomaticallyLiveData: LiveData<Boolean>

    var storageLocationLiveData: LiveData<StorageLocation>
    var storedIssueNumberLiveData: LiveData<Int>

    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(application)
    private val downloadDataStore = DownloadDataStore.getInstance(application)
    private val storageDataStore = StorageDataStore.getInstance(application)

    init {
        fontSizeLiveData = tazApiCssDataStore.fontSize.asLiveData()

        nightModeLiveData = tazApiCssDataStore.nightMode.asLiveData()

        storedIssueNumberLiveData = storageDataStore.keepIssuesNumber.asLiveData()
        storageLocationLiveData = storageDataStore.storageLocation.asLiveData()

        downloadOnlyWifiLiveData = downloadDataStore.onlyWifi.asLiveData()
        downloadAutomaticallyLiveData = downloadDataStore.enabled.asLiveData()
    }

    fun setStorageLocation(storageLocation: StorageLocation?) {
        if (storageLocation != null) {
            CoroutineScope(Dispatchers.IO).launch {
                storageDataStore.storageLocation.set(storageLocation)
            }
        }
    }

    suspend fun setKeepIssueNumber(number: Int) {
        storageDataStore.keepIssuesNumber.set(number)
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

    fun setPdfDownloadsEnabled(enabled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            downloadDataStore.pdfAdditionally.set(enabled)
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