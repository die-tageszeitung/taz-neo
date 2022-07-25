package de.taz.app.android.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.taz.app.android.*
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class SettingsViewModel(application: Application) : AndroidViewModel(application), CoroutineScope {

    var fontSizeLiveData: LiveData<String>
    var textJustificationLiveData: LiveData<Boolean>
    var nightModeLiveData: LiveData<Boolean>
    var tapToScrollLiveData: LiveData<Boolean>
    var keepScreenOnLiveData: LiveData<Boolean>

    val downloadOnlyWifiLiveData: LiveData<Boolean>
    val downloadAutomaticallyLiveData: LiveData<Boolean>
    val downloadAdditionallyPdf: LiveData<Boolean>
    private val downloadAdditionallyDialogDoNotShowAgain: LiveData<Boolean>
    val notificationsEnabledLivedata: LiveData<Boolean>

    var storageLocationLiveData: LiveData<StorageLocation>
    var storedIssueNumberLiveData: LiveData<Int>

    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(application)
    private val downloadDataStore = DownloadDataStore.getInstance(application)
    private val storageDataStore = StorageDataStore.getInstance(application)
    private val apiService = ApiService.getInstance(application)

    init {
        fontSizeLiveData = tazApiCssDataStore.fontSize.asLiveData()

        textJustificationLiveData = tazApiCssDataStore.textJustification.asLiveData()
        nightModeLiveData = tazApiCssDataStore.nightMode.asLiveData()
        tapToScrollLiveData = tazApiCssDataStore.tapToScroll.asLiveData()
        keepScreenOnLiveData = tazApiCssDataStore.keepScreenOn.asLiveData()

        storedIssueNumberLiveData = storageDataStore.keepIssuesNumber.asLiveData()
        storageLocationLiveData = storageDataStore.storageLocation.asLiveData()

        downloadOnlyWifiLiveData = downloadDataStore.onlyWifi.asLiveData()
        downloadAutomaticallyLiveData = downloadDataStore.enabled.asLiveData()
        downloadAdditionallyPdf = downloadDataStore.pdfAdditionally.asLiveData()
        downloadAdditionallyDialogDoNotShowAgain = downloadDataStore.pdfDialogDoNotShowAgain.asLiveData()
        notificationsEnabledLivedata = downloadDataStore.notificationsEnabled.asLiveData()
    }

    suspend fun setKeepIssueNumber(number: Int) {
        storageDataStore.keepIssuesNumber.set(number)
    }

    fun setOnlyWifi(onlyWifi: Boolean) {
        launch {
            downloadDataStore.onlyWifi.set(onlyWifi)
        }
    }

    fun setDownloadsEnabled(enabled: Boolean) {
        launch {
            downloadDataStore.enabled.set(enabled)
        }
    }

    fun setPdfDownloadsEnabled(enabled: Boolean) {
        launch {
            downloadDataStore.pdfAdditionally.set(enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        launch {
            downloadDataStore.notificationsEnabled.set(enabled)
            apiService.setNotificationsEnabled(enabled)
        }
    }

    fun setPdfDialogDoNotShowAgain(doNotShowAgain: Boolean) {
        launch {
            downloadDataStore.pdfDialogDoNotShowAgain.set(doNotShowAgain)
        }
    }

    fun resetFontSize() {
        launch {
            tazApiCssDataStore.fontSize.reset()
        }
    }

    fun decreaseFontSize() {
        launch {
            val newSize = getFontSize() - 10
            if (newSize >= MIN_TEXT_SIZE) {
                setFontSize(newSize.toString())
            }
        }
    }

    fun increaseFontSize() {
        launch {
            val newSize = getFontSize() + 10
            if (newSize <= MAX_TEST_SIZE) {
                setFontSize(newSize.toString())
            }
        }
    }

    private fun setFontSize(value: String) {
        launch {
            tazApiCssDataStore.fontSize.set(value)
        }
    }

    fun setTextJustification(justified: Boolean) {
        launch {
            tazApiCssDataStore.textJustification.set(justified)
        }
    }

    fun setNightMode(value: Boolean) {
        launch {
            tazApiCssDataStore.nightMode.set(value)
        }
    }

    fun setTapToScroll(value: Boolean) {
        launch {
            tazApiCssDataStore.tapToScroll.set(value)
        }
    }

    fun setKeepScreenOn(value: Boolean) {
        launch {
            tazApiCssDataStore.keepScreenOn.set(value)
        }
    }

    private suspend fun getFontSize(): Int = tazApiCssDataStore.fontSize.get().toInt()

    override val coroutineContext: CoroutineContext = SupervisorJob()
}