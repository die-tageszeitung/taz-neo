package de.taz.app.android.ui.webview

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HelpFabViewModel : ViewModel() {

    private val _showHelpFabFlow = MutableStateFlow(true)
    val showHelpFabFlow: StateFlow<Boolean> = _showHelpFabFlow.asStateFlow()

    fun showHelpFab() {
        _showHelpFabFlow.value = true
    }

    fun hideHelpFab() {
        _showHelpFabFlow.value = false
    }
}
