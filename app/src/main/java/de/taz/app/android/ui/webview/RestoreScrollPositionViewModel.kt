package de.taz.app.android.ui.webview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.taz.app.android.dataStore.GeneralDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn


class RestoreScrollPositionViewModel(
    application: Application,
): AndroidViewModel(application) {
    private val generalDataStore = GeneralDataStore.getInstance(application)

    // we ware only interested if the settings are enabled
    private val settingsContinueReadTrueFlow = generalDataStore.settingsContinueRead.asFlow()
        .filter { it }
    private val continueReadClickedFlow = generalDataStore.continueReadClicked.asFlow()
        // we ignore the current state of the setting
        .drop(1)
        // we only want events > 0 as otherwise the user continue read dismissed
        .filter { it > 0 }
        // we want a boolean so we map true
        .map { true }

    val restoreScrollStateFlow: Flow<Boolean> = merge(
        settingsContinueReadTrueFlow,
        continueReadClickedFlow,
    ).shareIn(viewModelScope, SharingStarted.Lazily,  1)
}
