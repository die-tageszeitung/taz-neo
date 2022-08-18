package de.taz.app.android.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import de.taz.app.android.*
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

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
    private val authHelper: AuthHelper = AuthHelper.getInstance(application)

    private val elapsedOnString = authHelper.elapsedDateMessage.asLiveData()
    val elapsedString = elapsedOnString.map { DateHelper.stringToMediumLocalizedString(it) }

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
        viewModelScope.launch {
            downloadDataStore.onlyWifi.set(onlyWifi)
        }
    }

    fun setDownloadsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            downloadDataStore.enabled.set(enabled)
        }
    }

    fun setPdfDownloadsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            downloadDataStore.pdfAdditionally.set(enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            downloadDataStore.notificationsEnabled.set(enabled)
            apiService.setNotificationsEnabled(enabled)
        }
    }

    fun setPdfDialogDoNotShowAgain(doNotShowAgain: Boolean) {
        viewModelScope.launch {
            downloadDataStore.pdfDialogDoNotShowAgain.set(doNotShowAgain)
        }
    }

    fun resetFontSize() {
        viewModelScope.launch {
            tazApiCssDataStore.fontSize.reset()
        }
    }

    fun decreaseFontSize() {
        viewModelScope.launch {
            val newSize = getFontSize() - 10
            if (newSize >= MIN_TEXT_SIZE) {
                setFontSize(newSize.toString())
            }
        }
    }

    fun increaseFontSize() {
        viewModelScope.launch {
            val newSize = getFontSize() + 10
            if (newSize <= MAX_TEST_SIZE) {
                setFontSize(newSize.toString())
            }
        }
    }

    private fun setFontSize(value: String) {
        viewModelScope.launch {
            tazApiCssDataStore.fontSize.set(value)
        }
    }

    fun setTextJustification(justified: Boolean) {
        viewModelScope.launch {
            tazApiCssDataStore.textJustification.set(justified)
        }
    }

    fun setNightMode(value: Boolean) {
        viewModelScope.launch {
            tazApiCssDataStore.nightMode.set(value)
        }
    }

    fun setTapToScroll(value: Boolean) {
        viewModelScope.launch {
            tazApiCssDataStore.tapToScroll.set(value)
        }
    }

    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch {
            tazApiCssDataStore.keepScreenOn.set(value)
        }
    }

    private suspend fun getFontSize(): Int = tazApiCssDataStore.fontSize.get().toInt()
}