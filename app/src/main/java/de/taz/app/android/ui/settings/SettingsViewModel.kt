package de.taz.app.android.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.taz.app.android.MAX_TEXT_SIZE
import de.taz.app.android.MIN_TEXT_SIZE
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val log by Log


    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(application.applicationContext)
    private val downloadDataStore = DownloadDataStore.getInstance(application.applicationContext)
    private val storageDataStore = StorageDataStore.getInstance(application.applicationContext)
    private val generalDataStore = GeneralDataStore.getInstance(application.applicationContext)
    private val apiService = ApiService.getInstance(application.applicationContext)
    private val authHelper: AuthHelper = AuthHelper.getInstance(application.applicationContext)

    val fontSizeFlow = tazApiCssDataStore.fontSize.asFlow()
    val keepScreenOnFlow = tazApiCssDataStore.keepScreenOn.asFlow()
    val multiColumnModeFlow = tazApiCssDataStore.multiColumnMode.asFlow()
    val nightModeFlow = tazApiCssDataStore.nightMode.asFlow()
    val tapToScrollFlow = tazApiCssDataStore.tapToScroll.asFlow()
    val textJustificationFlow = tazApiCssDataStore.textJustification.asFlow()

    val bookmarksSynchronization = generalDataStore.bookmarksSynchronizationEnabled.asFlow()
    val showAnimatedMomentsFlow = generalDataStore.showAnimatedMoments.asFlow()
    val hideAppbarOnScroll = generalDataStore.hideAppbarOnScroll.asFlow()
    val animateDrawerLogoFlow = generalDataStore.animateDrawerLogo.asFlow()
    val showContinueReadFlow = generalDataStore.settingsContinueRead.asFlow()
    val showContinueReadAskEachTimeFlow = generalDataStore.settingsContinueReadAskEachTime.asFlow()
    val trackingAccepted = generalDataStore.consentToTracking.asFlow()

    val downloadOnlyWifiFlow = downloadDataStore.onlyWifi.asFlow()
    val downloadAutomaticallyFlow = downloadDataStore.enabled.asFlow()
    val downloadAdditionallyPdf = downloadDataStore.pdfAdditionally.asFlow()
    val helpFabEnabledFlow = generalDataStore.helpFabEnabled.asFlow()
    val notificationsEnabledLivedata = downloadDataStore.notificationsEnabled.asFlow()

    val storageLocationFlow = storageDataStore.storageLocation.asFlow()
    val storedIssueNumberFlow = storageDataStore.keepIssuesNumber.asFlow()
    private val elapsedOnString = authHelper.elapsedDateMessage.asFlow()
    val elapsedString = elapsedOnString.map { DateHelper.stringToMediumLocalizedString(it) }

    fun increaseKeepIssueNumber() {
        viewModelScope.launch {
            val newVal = storageDataStore.keepIssuesNumber.get().plus(1)
            storageDataStore.keepIssuesNumber.set(newVal)
        }
    }

    fun decreaseKeepIssueNumber() {
        viewModelScope.launch {
            val newVal = storageDataStore.keepIssuesNumber.get().minus(1).coerceAtLeast(2)
            storageDataStore.keepIssuesNumber.set(newVal)
        }
    }

    fun resetKeepIssueNumber() {
        viewModelScope.launch {
            storageDataStore.resetKeepIssuesNumber()
        }
    }

    fun setOnlyWifi(onlyWifi: Boolean) {
        viewModelScope.launch {
            downloadDataStore.onlyWifi.set(onlyWifi)
        }
    }

    fun setBookmarksSynchronization(enabled: Boolean) {
        viewModelScope.launch {
            if (generalDataStore.bookmarksSynchronizationEnabled.get() != enabled) {
                // Set changed to [enabled] only once. It will indicate
                // special bookmark synchronization on first time.
                generalDataStore.bookmarksSynchronizationChangedToEnabled.set(enabled)
            }
            generalDataStore.bookmarksSynchronizationEnabled.set(enabled)
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

    fun setTrackingAccepted(isAccepted: Boolean) {
        viewModelScope.launch {
            generalDataStore.consentToTracking.set(isAccepted)
        }
    }

    /**
     * Returns the value of the final notification enabled state.
     * If the call was successful the return value will be equal to the passed [enabled] parameter.
     * Changing the notifications may fail if there is no internet as we try to inform the API server.
     */
    suspend fun setNotificationsEnabled(enabled: Boolean): Boolean {
        val current = downloadDataStore.notificationsEnabled.get()
        if (current != enabled) {
            try {
                apiService.setNotificationsEnabled(enabled)
                downloadDataStore.notificationsEnabled.set(enabled)
                return enabled
            } catch (exception: ConnectivityException) {
                log.error(
                    "Could not set notification status, as the backend is not available.",
                    exception
                )
                return current
            }
        }
        return current
    }

    suspend fun getNotificationsEnabled(): Boolean = downloadDataStore.notificationsEnabled.get()

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
            if (newSize <= MAX_TEXT_SIZE) {
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

    fun setMultiColumnMode(value: Boolean) {
        viewModelScope.launch {
            tazApiCssDataStore.multiColumnMode.set(value)
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

    fun setShowAnimatedMoments(value: Boolean) {
        viewModelScope.launch {
            generalDataStore.showAnimatedMoments.set(value)
        }
    }

    fun setHideAppbarOnScroll(value: Boolean) {
        viewModelScope.launch {
            generalDataStore.hideAppbarOnScroll.set(value)
        }
    }

    fun setAnimateDrawerLogo(value: Boolean) {
        viewModelScope.launch {
            generalDataStore.animateDrawerLogo.set(value)
        }
    }

    fun setContinueRead(value: Boolean) {
        viewModelScope.launch {
            generalDataStore.settingsContinueRead.set(value)
            generalDataStore.settingsContinueReadAskEachTime.set(false)
        }
    }

    fun setContinueReadAskEachTime(value: Boolean) {
        viewModelScope.launch {
            generalDataStore.settingsContinueReadAskEachTime.set(value)
            if (value) {
                generalDataStore.settingsContinueRead.set(false)
            }
        }
    }

    private suspend fun getFontSize(): Int = tazApiCssDataStore.fontSize.get().toInt()

    fun enableDebugSettings() {
        viewModelScope.launch {
            generalDataStore.debugSettingsEnabled.set(true)
        }
    }

    suspend fun areDebugSettingsEnabled(): Boolean {
        return generalDataStore.debugSettingsEnabled.get()
    }

    suspend fun getAppSessionCount(): Long {
        return generalDataStore.appSessionCount.get()
    }

    fun forceNewAppSession() {
        viewModelScope.launch {
            generalDataStore.lastMainActivityUsageTimeMs.set(0L)
        }
    }

    suspend fun getTestTrackingGoalStatus(): Boolean {
        return generalDataStore.testTrackingGoalEnabled.get()
    }

    fun setTestTrackingGoalStatus(enabled: Boolean) {
        viewModelScope.launch {
            generalDataStore.testTrackingGoalEnabled.set(enabled)
        }
    }

    fun setHelpFabEnabled(enabled: Boolean) = viewModelScope.launch {
        generalDataStore.helpFabEnabled.set(enabled)
    }
}